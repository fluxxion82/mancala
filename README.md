# Mancala AI

A Kalah (6,4) game with an AlphaZero-style engine I trained from self-play, built in Kotlin Multiplatform with Compose. Inference is pure Kotlin float math — no ONNX, no DJL, no native ML runtime — and in the browser the search runs in a Web Worker so the UI never blocks.

**Play it in the browser:** [sterlingalbury.com](https://sterlingalbury.com) → Projects → Mancala AI

## The engine

The model is a compact ResNet: four residual blocks of 768 hidden units with layer normalization, ~5.2M parameters. The board is encoded as 56 features and passed through a shared body with two heads:

- **Policy head** — a probability distribution over the six playable pits.
- **Value head** — a tanh-bounded score in [-1, 1] from the current player's point of view.

Move selection uses PUCT-MCTS ([`engine/ml/PuctMcts.kt`](src/commonMain/kotlin/ai/sterling/engine/ml/PuctMcts.kt)) guided by the network ([`engine/ml/NeuralNetEngine.kt`](src/commonMain/kotlin/ai/sterling/engine/ml/NeuralNetEngine.kt)), with tactical priors and extra simulations in endgames (2× at ≤12 stones remaining, 4× at ≤6).

An earlier attempt used PPO with a plain three-layer MLP. It learned legal moves but hit a ceiling — no lookahead, missed tactics. Switching to the AlphaZero recipe (search-improved policy targets + value bootstrapping) is what made it play well.

## Training

Training runs offline in a separate Python pipeline; this repo consumes the exported weights.

One iteration: self-play game generation (pure self-play plus fixed opponents — greedy, minimax, neural-minimax, the old PPO model) → replay buffer of (features, MCTS visit distribution, outcome) tuples → network updates in batches of 256 → evaluation against a battery of opponents, tactical positions, and endgame-solver agreement → checkpointing, where only candidates that improve the composite score and pass a promotion gate become the deployed model.

Two details that mattered:

- **Exact endgame solver as oracle.** When the solver can prove a position, its value replaces the game-outcome label. This fixed a value head that stayed optimistic in lost endgames — solver agreement is now ~94%.
- **Root-only Dirichlet noise** (60% prior / 40% noise, α=0.5) so openings don't collapse early.

Honest caveat: Kalah (6,4) is solved — perfect play wins for Player 1. That makes the engine's raw win rate less impressive than it sounds, and second-player play the harder training problem. It also keeps the game a good self-play testbed: the unsolved larger variants are the eventual target.

## Weights pipeline

Training exports NumPy `.npz` ([`model_source/`](model_source/)). A build step converts it to a custom little-endian f32 binary that `commonMain` parses with no zip or NumPy dependency ([`jvmMain/.../NpyReader.kt`](src/jvmMain/kotlin/ai/sterling/engine/ml)):

```bash
./gradlew convertWeights   # .npz → .bin
./gradlew compressWeights  # gzip for the web bundle (browser inflates via DecompressionStream)
```

## Running

```bash
./gradlew run                            # Compose desktop app
./gradlew wasmJsBrowserDevelopmentRun    # browser build
./gradlew jvmTest allTests               # engine + UI tests
```

On wasmJs the MCTS runs in a Web Worker through a backend factory override; once weights load, the game plays fully offline.

## Structure

```
src/commonMain/kotlin/ai/sterling/
  engine/ml/      NeuralNetEngine, PuctMcts, TacticalPriors, WeightsLoader
  engine/monte/   plain MCTS baseline + position evaluator
  model/          game rules, board state
  ui/             Compose board, sowing animation system
  viewmodel/      game flow
src/jvmMain/      desktop entry, NpyReader, weights converter
src/wasmJsMain/   browser entry, Web Worker inference backend
model_source/     trained weights (.npz)
```

## Rules (Kalah 6,4)

Six pits per side, four stones each, one scoring store (mancala) per player. Sow counter-clockwise; landing in your own mancala grants another turn; landing in an empty pit on your side captures the stones opposite. Most stones when one side empties wins.
