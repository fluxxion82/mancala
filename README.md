# Mancala AI

A Kalah (6,4) game with an AlphaZero-style engine I trained from self-play, built in Kotlin Multiplatform with Compose. Inference is pure Kotlin float math — no ONNX, no DJL, no native ML runtime — and in the browser the search runs in a Web Worker so the UI never blocks.

**Play it in the browser:** [sterlingalbury.com](https://sterlingalbury.com) → Projects → Mancala AI

## The engine

The model is a compact ResNet: four residual blocks of 768 hidden units with layer normalization, ~5.2M parameters. The board is encoded as 56 features and passed through a shared body with two heads:

- **Policy head** — a probability distribution over the six playable pits.
- **Value head** — a tanh-bounded score in [-1, 1] from the current player's point of view.

Move selection uses PUCT-MCTS ([`engine/ml/PuctMcts.kt`](engine/src/commonMain/kotlin/ai/sterling/engine/ml/PuctMcts.kt)) guided by the network ([`engine/ml/NeuralNetEngine.kt`](engine/src/commonMain/kotlin/ai/sterling/engine/ml/NeuralNetEngine.kt)), with tactical priors and extra simulations in endgames (2× at ≤12 stones remaining, 4× at ≤6).

An earlier attempt used PPO with a plain three-layer MLP. It learned legal moves but hit a ceiling — no lookahead, missed tactics. Switching to the AlphaZero recipe (search-improved policy targets + value bootstrapping) is what made it play well.

## Training

Training runs offline in a separate Python pipeline; this repo consumes the exported weights.

One iteration: self-play game generation (pure self-play plus fixed opponents — greedy, minimax, neural-minimax, the old PPO model) → replay buffer of (features, MCTS visit distribution, outcome) tuples → network updates in batches of 256 → evaluation against a battery of opponents, tactical positions, and endgame-solver agreement → checkpointing, where only candidates that improve the composite score and pass a promotion gate become the deployed model.

Two details that mattered:

- **Exact endgame solver as oracle.** When the solver can prove a position, its value replaces the game-outcome label. This fixed a value head that stayed optimistic in lost endgames — solver agreement is now ~94%.
- **Root-only Dirichlet noise** (60% prior / 40% noise, α=0.5) so openings don't collapse early.

Honest caveat: Kalah (6,4) is solved — perfect play wins for Player 1. That makes the engine's raw win rate less impressive than it sounds, and second-player play the harder training problem. It also keeps the game a good self-play testbed: the unsolved larger variants are the eventual target.

## Weights pipeline

Training exports NumPy `.npz` ([`model_source/`](model_source/)). A build step converts it to a custom little-endian f32 binary that `commonMain` parses with no zip or NumPy dependency ([`engine/src/jvmMain/.../NpyReader.kt`](engine/src/jvmMain/kotlin/ai/sterling/engine/ml)):

```bash
./gradlew convertWeights   # .npz → .bin
./gradlew compressWeights  # gzip for the web bundle (browser inflates via DecompressionStream)
```

## Running

```bash
./gradlew run                            # Compose desktop app
./gradlew wasmJsBrowserDevelopmentRun    # browser build
./gradlew jvmTest                        # engine + UI JVM tests
./gradlew :engine:jvmTest                # engine only
```

On wasmJs the MCTS runs in a Web Worker through a backend factory override; once weights load, the game plays fully offline.

## Structure

Two Gradle modules:

| Module | Coordinates | Contents | Depends on |
|---|---|---|---|
| `:engine` (`engine/`) | `ai.sterling:engine` | rules and model, alpha-beta + PUCT-MCTS, NN inference, weight parsing, `GameSession` | stdlib, kotlinx-coroutines, kotlinx-io. **No Compose, no lifecycle** |
| root | `ai.sterling:mancala` | Compose UI (`MancalaGame`, board, sowing animation, loading screen), `MancalaBoardViewModel`, the weights as Compose Resources | `api(project(":engine"))` |

Because of the `api` dependency, anything that depends on `ai.sterling:mancala` still sees every engine class under its original package. A front end that isn't Compose UI (e.g. a [Mosaic](https://github.com/JakeWharton/mosaic) terminal UI) depends on `:engine` only.

```
engine/src/commonMain/kotlin/ai/sterling/
  AiMode.kt, AiBackendFactory.kt   AiMode, createAiBackend(), MancalaBackendFactory
  model/          Board, Game, Player, HumanSide, MoveEvent
  engine/         AiBackend, MoveTelemetry
  engine/ml/      NeuralNetEngine, PuctMcts, TacticalPriors, WeightsLoader
  engine/monte/   plain MCTS baseline + position evaluator
  data/, repository/  GameRepository + InMemoryGameRepository
  session/        GameSession (UI-agnostic game flow)
  loading/        WeightLoadingState; jvm: readWeightBytes(); wasm: downloadWeightBytes()
  util/           GameLogger (jvm: ~/mancala_games.jsonl, wasm: remote log), MancalaDebug
engine/src/jvmMain/     NpyReader, AiBackend.jvm (single-thread AI dispatcher)
engine/src/wasmJsMain/  AiBackend.wasmJs (MancalaBackendFactory hook), weight downloader
src/commonMain/kotlin/ai/sterling/
  MancalaGame.kt  root composable, preloadMancalaWeights(), weight cache
  ui/             Compose board, sowing animation system
  viewmodel/      MancalaBoardViewModel (wraps GameSession)
  loading/        loading screen, fetchWeightBytes() per platform
src/commonMain/composeResources/files/  mancala_weights.bin (+ .bin.gz)
src/jvmMain/      desktop entry
model_source/     trained weights (.npz)
```

## Using the engine from another UI

Board indices: pits `0..5` are Player One's, `6` is P1's store, pits `7..12` are Player Two's, `13` is P2's store. P1 always moves first; landing in your own store gives you another turn, so check `status` after every move rather than assuming turns alternate.

**1. Load weights.** You need the uncompressed `mancala_weights.bin` (~20.7 MB, or ~19.2 MB gzipped). The engine does not bundle it.
- JVM: `ai.sterling.loading.readWeightBytes(File(...))` takes `.bin` or `.bin.gz` (gzip is detected from the bytes). Copies live in `src/commonMain/composeResources/files/`, and the site serves the gzipped one at `https://sterlingalbury.com/composeResources/ai.sterling.mancala.resources/files/mancala_weights.bin.gz` (`gunzipIfNeeded(bytes)` handles a download).
- wasmJs: `ai.sterling.loading.downloadWeightBytes(url = MANCALA_WEIGHTS_GZ_PATH, cacheKey = "...", onState = { ... })`. It checks the IndexedDB cache, then does `fetch`, then inflates with `DecompressionStream`, reporting progress as `WeightLoadingState`s.
- Compose UI only: `fetchWeightBytes` / `preloadMancalaWeights()` read the Compose Resource.

**2. Create a backend.** `ai.sterling.createAiBackend(bytes)` gives you an `AiBackend`. You can also call `NeuralNetEngine.create(searchDepth = 1, weightBytes = bytes).asBackend()`. On JVM the backend always runs in-process. On wasm, `createAiBackend` uses `MancalaBackendFactory.override` when the host set one; the website sets it to a Web Worker proxy (`WorkerEngineHost`). Otherwise it runs the search on the calling thread, which blocks the event loop for the whole time budget.

**3a. Let `GameSession` drive the game.** This is the easiest option, and the Compose ViewModel uses it too.

```kotlin
val session = GameSession(backend, AiMode.AlphaBeta(timeBudgetMs = 2300, maxDepth = 7), scope, gameLogger = null)
session.restart(HumanSide.PLAYER_ONE)          // starts a game; AI moves by itself when it's its turn
session.playHumanMove(2)                       // false if illegal (wrong side, empty pit, not your turn)
session.game.collect { g ->                    // StateFlow<Game>: g.board.pockets, g.status
    if (g.status is Game.GameStatus.Finished) println("game over: ${g.status}")
}
// also: session.events (MoveEvent.MoveApplied/Reset), session.isAiThinking, session.isLegalMove(pos)
```

**3b. Or drive it yourself.** `Game` is immutable, so each move returns a new one.

```kotlin
var game = Game.new()
game = game.makeMove(2)                                       // throws IllegalArgumentException if illegal
val legal = game.board.legalMoves(isPlayerOneTurn = game.status == Game.GameStatus.PlayerOneTurn)
val ai = runBlocking { backend.selectMove(game, AiMode.AlphaBeta()) }   // JVM: synchronous call
game = game.makeMove(ai)
val over = game.status is Game.GameStatus.Finished            // PlayerOneWin / PlayerTwoWin / Draw
```

`NeuralNetEngine` keeps a search tree and a transposition table between calls, so it isn't thread-safe. Make one call at a time, and call `backend.resetSearchState()` when you start a new game (`GameSession.restart` does this for you).

## Rules (Kalah 6,4)

Six pits per side, four stones each, one scoring store (mancala) per player. Sow counter-clockwise; landing in your own mancala grants another turn; landing in an empty pit on your side captures the stones opposite. Most stones when one side empties wins.
