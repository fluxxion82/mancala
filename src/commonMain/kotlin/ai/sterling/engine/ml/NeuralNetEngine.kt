package ai.sterling.engine.ml

import ai.sterling.engine.MoveTelemetry
import ai.sterling.mancala.resources.Res
import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import ai.sterling.util.MancalaDebug
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.ExperimentalResourceApi

class NeuralNetEngine private constructor(
    private val weights: ModelWeights,
    private val searchDepth: Int,
) {

    // V1 (plain MLP) fields
    private var v1Layers: List<Pair<Array<FloatArray>, FloatArray>>? = null
    private var v1ActionW: Array<FloatArray>? = null
    private var v1ActionB: FloatArray? = null
    private var v1ValueLayers: List<Pair<Array<FloatArray>, FloatArray>>? = null
    private var v1ValueW: Array<FloatArray>? = null
    private var v1ValueB: FloatArray? = null

    // V2 (ResNet + LayerNorm) fields
    private var v2ProjW: Array<FloatArray>? = null
    private var v2ProjB: FloatArray? = null
    private var v2ProjLnScale: FloatArray? = null
    private var v2ProjLnBias: FloatArray? = null
    private var v2ResBlocks: List<ResBlock>? = null
    private var v2PolicyW1: Array<FloatArray>? = null
    private var v2PolicyB1: FloatArray? = null
    private var v2PolicyLnScale: FloatArray? = null
    private var v2PolicyLnBias: FloatArray? = null
    private var v2PolicyW2: Array<FloatArray>? = null
    private var v2PolicyB2: FloatArray? = null
    private var v2ValueW1: Array<FloatArray>? = null
    private var v2ValueB1: FloatArray? = null
    private var v2ValueLnScale: FloatArray? = null
    private var v2ValueLnBias: FloatArray? = null
    private var v2ValueW2: Array<FloatArray>? = null
    private var v2ValueB2: FloatArray? = null

    private val version: Int
    private val activation: String
    private val inputDim: Int
    private val hasValueHead: Boolean

    private data class ResBlock(
        val w1: Array<FloatArray>, val b1: FloatArray,
        val ln1Scale: FloatArray, val ln1Bias: FloatArray,
        val w2: Array<FloatArray>, val b2: FloatArray,
        val ln2Scale: FloatArray, val ln2Bias: FloatArray,
    )

    init {
        version = weights.arrays["version"]?.firstOrNull()?.toInt() ?: 1

        if (version >= 2) {
            activation = "relu"
            val hiddenDim = weights.arrays["hidden_dim"]!!.first().toInt()
            val numBlocks = weights.arrays["num_blocks"]!!.first().toInt()
            inputDim = 56

            // Input projection
            v2ProjW = reshape(weights.arrays["proj_w"]!!, inputDim, hiddenDim)
            v2ProjB = weights.arrays["proj_b"]!!
            v2ProjLnScale = weights.arrays["proj_ln_scale"]!!
            v2ProjLnBias = weights.arrays["proj_ln_bias"]!!

            // Residual blocks
            val blocks = mutableListOf<ResBlock>()
            for (i in 0 until numBlocks) {
                blocks.add(
                    ResBlock(
                        w1 = reshape(weights.arrays["res${i}_w1"]!!, hiddenDim, hiddenDim),
                        b1 = weights.arrays["res${i}_b1"]!!,
                        ln1Scale = weights.arrays["res${i}_ln1_scale"]!!,
                        ln1Bias = weights.arrays["res${i}_ln1_bias"]!!,
                        w2 = reshape(weights.arrays["res${i}_w2"]!!, hiddenDim, hiddenDim),
                        b2 = weights.arrays["res${i}_b2"]!!,
                        ln2Scale = weights.arrays["res${i}_ln2_scale"]!!,
                        ln2Bias = weights.arrays["res${i}_ln2_bias"]!!,
                    ),
                )
            }
            v2ResBlocks = blocks

            // Policy head (2-layer)
            v2PolicyW1 = reshape(weights.arrays["policy_w1"]!!, hiddenDim, 256)
            v2PolicyB1 = weights.arrays["policy_b1"]!!
            v2PolicyLnScale = weights.arrays["policy_ln_scale"]!!
            v2PolicyLnBias = weights.arrays["policy_ln_bias"]!!
            v2PolicyW2 = reshape(weights.arrays["policy_w2"]!!, 256, 6)
            v2PolicyB2 = weights.arrays["policy_b2"]!!

            // Value head (2-layer)
            v2ValueW1 = reshape(weights.arrays["value_w1"]!!, hiddenDim, 256)
            v2ValueB1 = weights.arrays["value_b1"]!!
            v2ValueLnScale = weights.arrays["value_ln_scale"]!!
            v2ValueLnBias = weights.arrays["value_ln_bias"]!!
            v2ValueW2 = reshape(weights.arrays["value_w2"]!!, 256, 1)
            v2ValueB2 = weights.arrays["value_b2"]!!

            hasValueHead = true
        } else {
            // V1: plain MLP
            val nLayers = weights.arrays["n_layers"]!!.first().toInt()
            activation = weights.strings["activation"] ?: "tanh"

            val layerList = mutableListOf<Pair<Array<FloatArray>, FloatArray>>()
            for (i in 0 until nLayers) {
                val w = weights.arrays["fe_w$i"]!!
                val b = weights.arrays["fe_b$i"]!!
                val outputDim = b.size
                val inferredInputDim = if (i == 0) w.size / outputDim else layerList[i - 1].second.size
                layerList.add(Pair(reshape(w, inferredInputDim, outputDim), b))
            }
            v1Layers = layerList
            inputDim = layerList[0].first.size

            val lastHiddenSize = layerList.last().second.size
            v1ActionW = reshape(weights.arrays["action_w"]!!, lastHiddenSize, 6)
            v1ActionB = weights.arrays["action_b"]!!

            hasValueHead = weights.arrays.containsKey("value_w") && weights.arrays.containsKey("value_b")

            val vfNLayers = weights.arrays["vf_n_layers"]?.firstOrNull()?.toInt() ?: 0
            if (hasValueHead && vfNLayers > 0) {
                val vfList = mutableListOf<Pair<Array<FloatArray>, FloatArray>>()
                for (i in 0 until vfNLayers) {
                    val w = weights.arrays["vf_w$i"]!!
                    val b = weights.arrays["vf_b$i"]!!
                    val outputDim = b.size
                    val vfInputDim = if (i == 0) inputDim else vfList[i - 1].second.size
                    vfList.add(Pair(reshape(w, vfInputDim, outputDim), b))
                }
                v1ValueLayers = vfList
                val vfHiddenSize = vfList.last().second.size
                v1ValueW = reshape(weights.arrays["value_w"]!!, vfHiddenSize, 1)
                v1ValueB = weights.arrays["value_b"]!!
            } else if (hasValueHead) {
                v1ValueLayers = emptyList()
                v1ValueW = reshape(weights.arrays["value_w"]!!, lastHiddenSize, 1)
                v1ValueB = weights.arrays["value_b"]!!
            } else {
                v1ValueLayers = emptyList()
                v1ValueW = arrayOf(floatArrayOf(0f))
                v1ValueB = floatArrayOf(0f)
            }
        }
    }

    /**
     * Fixed-depth move selection. Suspending so the inference can yield control back
     * to the event loop on platforms with no real threading (Kotlin/Wasm). On JVM/Native
     * this still resolves quickly because [yield] just bounces through the dispatcher;
     * on Wasm the yields between top-level move evaluations let scroll, animations, and
     * pointer events run while the AI thinks instead of freezing the tab.
     */
    suspend fun selectMove(game: Game): Int {
        val isPlayerOne = game.status == GameStatus.PlayerOneTurn

        return if (hasValueHead && searchDepth > 0) {
            selectMoveWithSearch(game.board, isPlayerOne, searchDepth)
        } else {
            selectMovePolicy(game.board, isPlayerOne)
        }
    }

    /**
     * Iterative-deepening move selection — searches at depth 1, then depth 2, then 3,
     * etc., always keeping the best move from the deepest *completed* search. Stops
     * when starting the next depth would likely bust the time budget. Adapts naturally
     * to hardware: a fast device reaches deeper plies than a slow one within the same
     * wall-clock window, so the same code scales from a phone browser up to a desktop.
     *
     * Use this when you want "as strong as the device can afford" rather than a fixed
     * depth.
     */
    suspend fun selectMoveAdaptive(
        game: Game,
        timeBudgetMs: Long,
        maxDepth: Int = 6,
    ): Int {
        val isPlayerOne = game.status == GameStatus.PlayerOneTurn

        if (!hasValueHead) return selectMovePolicy(game.board, isPlayerOne)

        // Always have a fallback ready — pure policy-network move, ~one forward pass.
        var bestMove = selectMovePolicy(game.board, isPlayerOne)
        var reachedDepth = 0

        val mark = TimeSource.Monotonic.markNow()
        var lastDepthMs = 0L

        for (depth in 1..maxDepth) {
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            // Each ply roughly multiplies time by branching factor (~6 for Mancala
            // before alpha-beta + policy ordering shrinks it). Don't start the next
            // iteration if we'd almost certainly blow the budget.
            val estimatedNextMs = lastDepthMs * 5
            if (reachedDepth > 0 && elapsedMs + estimatedNextMs > timeBudgetMs) break

            val depthStartMs = elapsedMs
            bestMove = selectMoveWithSearch(game.board, isPlayerOne, depth)
            lastDepthMs = mark.elapsedNow().inWholeMilliseconds - depthStartMs
            reachedDepth = depth

            yield()
        }

        MancalaDebug.log {
            "[mancala] adaptive search: depth=$reachedDepth time=${mark.elapsedNow().inWholeMilliseconds}ms budget=${timeBudgetMs}ms"
        }
        return bestMove
    }

    /**
     * AlphaZero-style PUCT MCTS, guided by the policy + value heads. See [PuctMcts].
     *
     * - [simulations] caps the number of tree visits per move (0 = no cap).
     * - [timeBudgetMs] caps wall-clock per move (0 = no cap).
     * - If both are 0, the search defaults to 200 simulations.
     *
     * If the model has no value head we fall back to a single policy-only forward pass
     * — there's no signal for tree search to operate on without a value head.
     */
    // Persistent search tree across selectMoveMcts calls. After each search the root
    // is advanced to the chosen child so the next call (after the opponent moves)
    // can locate the new game state via PuctMcts.locateOrAdvance and inherit all the
    // accumulated visit counts. Reset to null when:
    //   - the engine is constructed fresh (no prior tree)
    //   - a forced-move shortcut bypasses search (we don't know which subtree to keep)
    //   - the cached subtree doesn't contain the new game state (rare, e.g. opponent
    //     played a move we never visited under the old root's exploration budget)
    private var currentRoot: PuctNode? = null

    /**
     * Counts AI moves played in the current game. Used to gate the opening
     * temperature window: while this is below `AiMode.Mcts.openingTempPlies`
     * the engine samples from the root visit-count distribution; after, it
     * falls back to deterministic argmax. Reset to 0 when [resetSearchState]
     * is called.
     */
    private var aiMoveIndexThisGame: Int = 0

    /**
     * Telemetry from the most recent [selectMoveMcts] call. `null` until at least
     * one MCTS search has run on this engine instance, or after [resetSearchState]
     * is called. Surfaced via [InProcessAiBackend.lastSearchTelemetry] for the
     * repository to log on each AI move.
     */
    var lastTelemetry: MoveTelemetry? = null
        private set

    /**
     * Reset the persistent search tree. Call this when starting a new game or when
     * the game state has been externally mutated in a way that the cached tree
     * can't follow (e.g. a programmatic restart).
     */
    fun resetSearchState() {
        currentRoot = null
        lastTelemetry = null
        aiMoveIndexThisGame = 0
    }

    suspend fun selectMoveMcts(
        game: Game,
        simulations: Int = 0,
        timeBudgetMs: Long = 0L,
        cPuct: Float = 1.5f,
        neuralWeight: Float = 0.7f,
        openingTempPlies: Int = 0,
        openingTemperature: Float = 1.0f,
    ): Int {
        val isPlayerOne = game.status == GameStatus.PlayerOneTurn
        if (!hasValueHead) return selectMovePolicy(game.board, isPlayerOne)

        // Forced-move shortcut: if there's only one legal move, take it without
        // searching. We don't know which subtree corresponds to the post-move state
        // without running expansion, so just discard the cached tree.
        val legal = game.board.legalMoves(isPlayerOne)
        if (legal.size == 1) {
            currentRoot = null
            MancalaDebug.log { "[mancala] forced move: ${legal[0]}" }
            return legal[0]
        }

        val (hitsBefore, missesBefore, _) = ttSnapshot()

        // Build a fresh PuctMcts per call so cPuct can be tuned without invalidating
        // the persistent root. The PuctMcts struct itself is tiny — just three method
        // closures and a constant. The accumulated search state lives on PuctNode.
        val mcts = PuctMcts(
            policyValue = { board, isP1 -> policyAndValue(board, isP1, neuralWeight) },
            cPuct = cPuct,
        )

        // Tree reuse: walk the cached tree to find the current game state. If found,
        // we inherit all its accumulated statistics; otherwise build a fresh root.
        val cached = currentRoot?.let { mcts.locateOrAdvance(it, game) }
        val root = cached ?: mcts.freshRoot(game)
        val reusedVisits = if (cached != null) cached.visitCount else 0

        val deterministicMove = mcts.search(root, simulations, timeBudgetMs)

        // Opening variety: for the first N AI moves of a game, sample from the root
        // visit-count distribution with temperature instead of taking the argmax.
        // Search itself is unchanged — same priors, same visits — only the final
        // root pick is randomized. Proven wins/losses remain deterministic;
        // see [sampleVisitsWithTemperature].
        val finalMove = if (
            aiMoveIndexThisGame < openingTempPlies &&
            openingTemperature > 0f &&
            root.provenValue == null
        ) {
            sampleVisitsWithTemperature(root, openingTemperature)
        } else {
            deterministicMove
        }
        aiMoveIndexThisGame++

        // Capture telemetry BEFORE re-rooting (root.n / root.prior are still the
        // search-just-completed values; after re-rooting we'd be looking at the
        // chosen child's data instead). The root-value lookup is a TT cache hit
        // so it's effectively free.
        val chosenRel = if (root.isPlayerOne) finalMove else finalMove - 7
        val chosenQ = if (root.n[chosenRel] > 0) root.w[chosenRel] / root.n[chosenRel] else 0f
        val (_, rootValue) = policyAndValue(root.game.board, root.isPlayerOne, neuralWeight)
        val (hitsAfter, missesAfter, _) = ttSnapshot()
        lastTelemetry = MoveTelemetry(
            move = finalMove,
            sims = mcts.lastSims,
            timeMs = mcts.lastTimeMs,
            rootValue = rootValue,
            priors = root.prior.copyOf(),
            visits = root.n.copyOf(),
            chosenQ = chosenQ,
            cacheHits = hitsAfter - hitsBefore,
            cacheMisses = missesAfter - missesBefore,
            reusedRootVisits = reusedVisits,
        )

        // Re-root for the next call: cache the subtree under the chosen move so that
        // when the opponent responds we can pick up where we left off.
        currentRoot = root.children[chosenRel]

        MancalaDebug.log {
            val total = (hitsAfter - hitsBefore) + (missesAfter - missesBefore)
            val pct = if (total > 0) ((hitsAfter - hitsBefore) * 100 / total) else 0
            "[mancala] tt: hits=${hitsAfter - hitsBefore} misses=${missesAfter - missesBefore} (${pct}%) cacheSize=${ttCache.size} reusedRootVisits=$reusedVisits"
        }
        return finalMove
    }

    /**
     * Sample a move from the root's visit-count distribution with the given
     * temperature: `P(a) ∝ visits(a)^(1/T)`. Used for opening variety; see the
     * `openingTempPlies` knob on [ai.sterling.AiMode.Mcts].
     *
     * Safety rails: a proven-win child is taken deterministically (we never
     * randomize away a forced win), proven-loss children are excluded from the
     * pool, and if no usable visits exist the result falls back to the visit-
     * count argmax.
     */
    private fun sampleVisitsWithTemperature(root: PuctNode, temperature: Float): Int {
        // Always take a proven-win child if one exists.
        for (rel in 0 until 6) {
            if (!root.legalRel[rel]) continue
            val child = root.children[rel] ?: continue
            val cv = child.provenValue ?: continue
            val asMover = if (root.isPlayerOne != child.isPlayerOne) -cv else cv
            if (asMover >= 1f - 1e-6f) {
                return if (root.isPlayerOne) rel else rel + 7
            }
        }

        val invTemp = (1f / temperature).toDouble()
        val weights = DoubleArray(6)
        var sum = 0.0
        for (rel in 0 until 6) {
            if (!root.legalRel[rel]) continue
            val child = root.children[rel]
            val cv = child?.provenValue
            if (cv != null) {
                val asMover = if (root.isPlayerOne != child.isPlayerOne) -cv else cv
                if (asMover <= -1f + 1e-6f) continue // proven loss — skip
            }
            if (root.n[rel] <= 0) continue
            val w = root.n[rel].toDouble().pow(invTemp)
            weights[rel] = w
            sum += w
        }

        if (sum <= 0.0) return visitArgmaxFallback(root)

        val r = Random.Default.nextDouble() * sum
        var cum = 0.0
        for (rel in 0 until 6) {
            if (weights[rel] <= 0.0) continue
            cum += weights[rel]
            if (cum >= r) return if (root.isPlayerOne) rel else rel + 7
        }
        // Numerical fallback (shouldn't happen with positive sum).
        return visitArgmaxFallback(root)
    }

    /** Visit-count argmax over legal moves; used when sampling can't proceed. */
    private fun visitArgmaxFallback(root: PuctNode): Int {
        var bestRel = -1
        var bestN = -1
        for (rel in 0 until 6) {
            if (!root.legalRel[rel]) continue
            if (root.n[rel] > bestN) {
                bestN = root.n[rel]
                bestRel = rel
            }
        }
        if (bestRel < 0) {
            // No moves visited at all — fall back to the prior's argmax.
            var bestPrior = -1f
            for (rel in 0 until 6) {
                if (root.legalRel[rel] && root.prior[rel] > bestPrior) {
                    bestPrior = root.prior[rel]
                    bestRel = rel
                }
            }
        }
        if (bestRel < 0) bestRel = 0
        return if (root.isPlayerOne) bestRel else bestRel + 7
    }

    private fun selectMovePolicy(board: Board, isPlayerOne: Boolean): Int {
        val obs = buildObs(board, isPlayerOne)
        val logits = policyForward(obs)

        val legalMoves = board.legalMoves(isPlayerOne)
        val legalIndices = if (isPlayerOne) legalMoves.toSet() else legalMoves.map { it - 7 }.toSet()

        for (i in 0 until 6) {
            if (i !in legalIndices) logits[i] = -1e8f
        }

        val bestAction = logits.indices.maxBy { logits[it] }
        return if (isPlayerOne) bestAction else bestAction + 7
    }

    private suspend fun selectMoveWithSearch(board: Board, isPlayerOne: Boolean, depth: Int): Int {
        val legalMoves = board.legalMoves(isPlayerOne)
        if (legalMoves.isEmpty()) return 0

        val obs = buildObs(board, isPlayerOne)
        val logits = policyForward(obs)

        val relativeActions = if (isPlayerOne) {
            legalMoves.sortedByDescending { logits[it] }
        } else {
            legalMoves.sortedByDescending { logits[it - 7] }
        }

        var bestMove = relativeActions[0]
        var bestScore = Float.NEGATIVE_INFINITY

        for (move in relativeActions) {
            // Cooperative yield: on Kotlin/Wasm this lets the browser process scroll,
            // animation frames, and pointer events between move evaluations.
            yield()
            val result = board.playMove(move, isPlayerOne)
            val score = if (result.board.isGameOver()) {
                evaluateBoard(result.board, isPlayerOne)
            } else if (result.endsInMancala) {
                minimax(result.board, depth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, isPlayerOne, true, isPlayerOne)
            } else {
                minimax(result.board, depth - 1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, !isPlayerOne, false, isPlayerOne)
            }

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove
    }

    private fun minimax(
        board: Board, depth: Int, alpha: Float, beta: Float,
        currentPlayer: Boolean, isMaximizing: Boolean, evalPlayer: Boolean
    ): Float {
        val legalMoves = board.legalMoves(currentPlayer)

        if (legalMoves.isEmpty() || depth <= 0) {
            return if (legalMoves.isEmpty()) {
                evaluateTerminal(board, evalPlayer)
            } else {
                evaluateBoard(board, evalPlayer)
            }
        }

        val obs = buildObs(board, currentPlayer)
        val logits = policyForward(obs)
        val sortedMoves = if (currentPlayer) {
            legalMoves.sortedByDescending { logits[it] }
        } else {
            legalMoves.sortedByDescending { logits[it - 7] }
        }

        var a = alpha
        var b = beta

        if (isMaximizing) {
            var maxScore = Float.NEGATIVE_INFINITY
            for (move in sortedMoves) {
                val result = board.playMove(move, currentPlayer)
                val score = if (result.board.isGameOver()) {
                    evaluateTerminal(result.board, evalPlayer)
                } else if (result.endsInMancala) {
                    minimax(result.board, depth, a, b, currentPlayer, true, evalPlayer)
                } else {
                    minimax(result.board, depth - 1, a, b, !currentPlayer, false, evalPlayer)
                }
                maxScore = max(maxScore, score)
                a = max(a, score)
                if (b <= a) break
            }
            return maxScore
        } else {
            var minScore = Float.POSITIVE_INFINITY
            for (move in sortedMoves) {
                val result = board.playMove(move, currentPlayer)
                val score = if (result.board.isGameOver()) {
                    evaluateTerminal(result.board, evalPlayer)
                } else if (result.endsInMancala) {
                    minimax(result.board, depth, a, b, currentPlayer, false, evalPlayer)
                } else {
                    minimax(result.board, depth - 1, a, b, !currentPlayer, true, evalPlayer)
                }
                minScore = min(minScore, score)
                b = min(b, score)
                if (b <= a) break
            }
            return minScore
        }
    }

    private fun evaluateBoard(board: Board, asPlayerOne: Boolean): Float {
        val obs = buildObs(board, asPlayerOne)
        val neuralValue = valueForward(obs)
        val heuristicValue = computeHeuristicValue(board, asPlayerOne)
        return 0.7f * neuralValue + 0.3f * heuristicValue
    }

    /**
     * Mancala-difference heuristic from the current mover's POV, scaled to roughly
     * `[-1, 1]`: weighs the mancala-mancala gap most, with a smaller bias from
     * stones-on-side difference. Used as the heuristic side of the value blend in
     * both [evaluateBoard] (alpha-beta path) and [policyAndValue] (MCTS path when
     * `neuralWeight < 1`).
     */
    private fun computeHeuristicValue(board: Board, asPlayerOne: Boolean): Float {
        val pockets = board.pockets
        val myMancala = if (asPlayerOne) pockets[Board.PLAYER_ONE_MANCALA] else pockets[Board.PLAYER_TWO_MANCALA]
        val oppMancala = if (asPlayerOne) pockets[Board.PLAYER_TWO_MANCALA] else pockets[Board.PLAYER_ONE_MANCALA]
        val mySide = if (asPlayerOne) (0..5).sumOf { pockets[it] } else (7..12).sumOf { pockets[it] }
        val oppSide = if (asPlayerOne) (7..12).sumOf { pockets[it] } else (0..5).sumOf { pockets[it] }
        return (myMancala - oppMancala + 0.3f * (mySide - oppSide)) / 48f
    }

    private fun evaluateTerminal(board: Board, asPlayerOne: Boolean): Float {
        val myMancala = if (asPlayerOne) board.pockets[Board.PLAYER_ONE_MANCALA] else board.pockets[Board.PLAYER_TWO_MANCALA]
        val oppMancala = if (asPlayerOne) board.pockets[Board.PLAYER_TWO_MANCALA] else board.pockets[Board.PLAYER_ONE_MANCALA]
        return (myMancala - oppMancala).toFloat() / 48f
    }

    private fun buildObs(board: Board, isPlayerOne: Boolean): FloatArray {
        val pockets = board.pockets
        val baseObs = if (isPlayerOne) {
            FloatArray(14) { pockets[it].toFloat() / 48f }
        } else {
            floatArrayOf(
                pockets[7].toFloat() / 48f, pockets[8].toFloat() / 48f, pockets[9].toFloat() / 48f,
                pockets[10].toFloat() / 48f, pockets[11].toFloat() / 48f, pockets[12].toFloat() / 48f,
                pockets[13].toFloat() / 48f,
                pockets[0].toFloat() / 48f, pockets[1].toFloat() / 48f, pockets[2].toFloat() / 48f,
                pockets[3].toFloat() / 48f, pockets[4].toFloat() / 48f, pockets[5].toFloat() / 48f,
                pockets[6].toFloat() / 48f,
            )
        }

        return when (inputDim) {
            56 -> {
                val actionFeatures = computeActionFeatures(board, isPlayerOne)
                val chainDepths = computeChainDepths(board, isPlayerOne)
                val opponentFeatures = computeActionFeatures(board, !isPlayerOne)
                FloatArray(56) { i ->
                    when {
                        i < 14 -> baseObs[i]
                        i < 32 -> actionFeatures[i - 14]
                        i < 38 -> chainDepths[i - 32]
                        else -> opponentFeatures[i - 38]
                    }
                }
            }
            32 -> {
                val actionFeatures = computeActionFeatures(board, isPlayerOne)
                FloatArray(32) { i -> if (i < 14) baseObs[i] else actionFeatures[i - 14] }
            }
            else -> baseObs
        }
    }

    // ---- Forward pass dispatching ----

    private fun policyForward(input: FloatArray): FloatArray {
        return if (version >= 2) policyForwardV2(input) else policyForwardV1(input)
    }

    private fun valueForward(input: FloatArray): Float {
        return if (version >= 2) valueForwardV2(input) else valueForwardV1(input)
    }

    /**
     * Combined forward pass returning both policy logits and value scalar. On V2
     * this folds the shared residual body so both heads see it once instead of
     * twice — roughly halves the per-call cost on a cache miss. On V1 the heads
     * have separate body layers, so there's no shared work to dedupe; this just
     * calls them in sequence.
     */
    private fun policyAndValueForward(input: FloatArray): Pair<FloatArray, Float> {
        return if (version >= 2) {
            val shared = sharedBodyV2(input)
            policyHeadV2(shared) to valueHeadV2(shared)
        } else {
            policyForwardV1(input) to valueForwardV1(input)
        }
    }

    // Transposition table: caches (board, mover) → (priors, value). The same Mancala
    // position is reached via many different move orderings (especially through
    // mancala-landing chains), so a TT hit avoids a ~50ms forward pass on Wasm. The
    // priors and value are pure functions of the input state, so caching is safe so
    // long as no caller mutates the returned arrays — PuctMcts only reads them.
    private val ttCache = HashMap<Long, Pair<FloatArray, Float>>()
    private val ttMaxSize = 50_000
    private var ttHits = 0L
    private var ttMisses = 0L

    /**
     * Combined policy + value evaluation for [PuctMcts]. Returns:
     *   - priors: a 6-vector with softmaxed policy logits over LEGAL relative actions
     *     (illegal slots zeroed); priors are from the perspective of whoever's turn it
     *     is on [board].
     *   - value: scalar in roughly (-1, 1) from the same mover's perspective. When
     *     [neuralWeight] < 1, blends the network's value head with a heuristic:
     *     `neuralWeight * neural + (1 - neuralWeight) * heuristic`.
     *
     * The legal-mask + softmax is applied here so MCTS can treat the priors as a
     * proper distribution over its 6 edge slots without any additional cleanup.
     *
     * Memoized via a transposition table keyed by board pockets + mover. The TT
     * caches the *raw* network output so the [neuralWeight] blend can change
     * between calls (e.g. for A/B testing) without invalidating cache entries.
     */
    internal fun policyAndValue(
        board: Board,
        isPlayerOne: Boolean,
        neuralWeight: Float = 1.0f,
    ): Pair<FloatArray, Float> {
        val key = boardHashKey(board, isPlayerOne)
        val cached = ttCache[key]
        val priors: FloatArray
        val rawNeural: Float
        if (cached != null) {
            ttHits++
            priors = cached.first
            rawNeural = cached.second
        } else {
            ttMisses++
            val (newPriors, newValue) = computePolicyAndRawValue(board, isPlayerOne)
            cacheStore(key, newPriors, newValue)
            priors = newPriors
            rawNeural = newValue
        }
        val blended = if (neuralWeight >= 0.999f) {
            rawNeural
        } else {
            val heuristic = computeHeuristicValue(board, isPlayerOne)
            neuralWeight * rawNeural + (1f - neuralWeight) * heuristic
        }
        // Clamp Q to [-1, 1]. V2's value head already saturates via tanh, but V1's
        // doesn't (and the heuristic blend can push out of range when the network
        // is poorly calibrated). PUCT's exploration term assumes Q ∈ [-1, 1]; an
        // out-of-range Q dominates the prior contribution and breaks tactical
        // search.
        val finalValue = blended.coerceIn(-1f, 1f)
        return priors to finalValue
    }

    /** Single forward pass + softmax + legal-mask. Used as the cache-miss path. */
    private fun computePolicyAndRawValue(board: Board, isPlayerOne: Boolean): Pair<FloatArray, Float> {
        val obs = buildObs(board, isPlayerOne)
        // One pass for both heads — on V2 this avoids re-running the shared residual
        // body twice (cuts cache-miss inference cost roughly in half).
        val (logits, value) = policyAndValueForward(obs)

        val legal = board.legalMoves(isPlayerOne)
        val legalRel = if (isPlayerOne) legal else legal.map { it - 7 }

        val priors = FloatArray(6)
        if (legalRel.isNotEmpty()) {
            var maxL = Float.NEGATIVE_INFINITY
            for (i in legalRel) if (logits[i] > maxL) maxL = logits[i]
            var sum = 0f
            for (i in legalRel) {
                val e = kotlin.math.exp((logits[i] - maxL).toDouble()).toFloat()
                priors[i] = e
                sum += e
            }
            if (sum > 0f) for (i in legalRel) priors[i] /= sum

            if (legalRel.size > 1) {
                val af = computeActionFeatures(board, isPlayerOne)
                blendTacticalPrior(priors, af, legalRel)
            }
        }
        return priors to value
    }

    /** Returns the running hit/miss totals since engine construction (for diagnostics). */
    internal fun ttSnapshot(): Triple<Long, Long, Int> = Triple(ttHits, ttMisses, ttCache.size)

    private fun cacheStore(key: Long, priors: FloatArray, value: Float) {
        // Skip-on-full keeps the existing entries warm. The alternative (full clear)
        // throws away thousands of useful evals to make room for the next position;
        // skipping just costs one extra forward pass per future cache miss until the
        // game ends. With a 5s desktop budget the cache fills exactly once, so the
        // next move starts hot.
        if (ttCache.size >= ttMaxSize) return
        ttCache[key] = priors to value
    }

    private fun boardHashKey(board: Board, isPlayerOne: Boolean): Long {
        // Polynomial accumulator over the 14 pockets + mover bit. Pocket counts are
        // bounded by 48 (6 bits each), so 14 × 6 = 84 bits of entropy compressed into
        // a 64-bit hash — collisions are essentially impossible for reachable states.
        var h = if (isPlayerOne) 1L else 0L
        for (i in 0 until 14) {
            h = h * 31L + board.pockets[i].toLong()
        }
        return h
    }

    // ---- V1 forward passes ----

    private fun policyForwardV1(input: FloatArray): FloatArray {
        var x = input
        for ((w, b) in v1Layers!!) {
            x = matVecMul(x, w, b)
            x = activate(x)
        }
        return matVecMul(x, v1ActionW!!, v1ActionB!!)
    }

    private fun valueForwardV1(input: FloatArray): Float {
        var x = input
        val bodyLayers = if (v1ValueLayers!!.isEmpty()) v1Layers!! else v1ValueLayers!!
        for ((w, b) in bodyLayers) {
            x = matVecMul(x, w, b)
            x = activate(x)
        }
        val out = matVecMul(x, v1ValueW!!, v1ValueB!!)
        return out[0]
    }

    // ---- V2 forward passes (ResNet + LayerNorm) ----

    private fun sharedBodyV2(input: FloatArray): FloatArray {
        // Input projection
        var x = matVecMul(input, v2ProjW!!, v2ProjB!!)
        x = layerNorm(x, v2ProjLnScale!!, v2ProjLnBias!!)
        x = relu(x)

        // Residual blocks
        for (block in v2ResBlocks!!) {
            val residual = x
            var out = matVecMul(x, block.w1, block.b1)
            out = layerNorm(out, block.ln1Scale, block.ln1Bias)
            out = relu(out)
            out = matVecMul(out, block.w2, block.b2)
            out = layerNorm(out, block.ln2Scale, block.ln2Bias)
            // Residual connection
            for (i in out.indices) out[i] += residual[i]
            x = relu(out)
        }

        return x
    }

    private fun policyForwardV2(input: FloatArray): FloatArray =
        policyHeadV2(sharedBodyV2(input))

    private fun valueForwardV2(input: FloatArray): Float =
        valueHeadV2(sharedBodyV2(input))

    /** Policy head: Linear -> LN -> ReLU -> Linear. */
    private fun policyHeadV2(shared: FloatArray): FloatArray {
        var p = matVecMul(shared, v2PolicyW1!!, v2PolicyB1!!)
        p = layerNorm(p, v2PolicyLnScale!!, v2PolicyLnBias!!)
        p = relu(p)
        return matVecMul(p, v2PolicyW2!!, v2PolicyB2!!)
    }

    /** Value head: Linear -> LN -> ReLU -> Linear -> tanh. */
    private fun valueHeadV2(shared: FloatArray): Float {
        var v = matVecMul(shared, v2ValueW1!!, v2ValueB1!!)
        v = layerNorm(v, v2ValueLnScale!!, v2ValueLnBias!!)
        v = relu(v)
        val out = matVecMul(v, v2ValueW2!!, v2ValueB2!!)
        return tanh(out[0].toDouble()).toFloat()
    }

    // ---- Utility functions ----

    private fun activate(x: FloatArray): FloatArray = when (activation) {
        "relu" -> relu(x)
        else -> FloatArray(x.size) { tanh(x[it].toDouble()).toFloat() }
    }

    private fun relu(x: FloatArray): FloatArray = FloatArray(x.size) { max(0f, x[it]) }

    private fun layerNorm(x: FloatArray, scale: FloatArray, bias: FloatArray, eps: Float = 1e-5f): FloatArray {
        val n = x.size
        var mean = 0f
        for (v in x) mean += v
        mean /= n

        var variance = 0f
        for (v in x) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= n

        val invStd = 1f / sqrt(variance + eps)
        return FloatArray(n) { i -> (x[i] - mean) * invStd * scale[i] + bias[i] }
    }

    private fun computeActionFeatures(board: Board, isPlayerOne: Boolean): FloatArray {
        val pockets = board.pockets
        val ownMancala = if (isPlayerOne) Board.PLAYER_ONE_MANCALA else Board.PLAYER_TWO_MANCALA
        val skipMancala = if (isPlayerOne) Board.PLAYER_TWO_MANCALA else Board.PLAYER_ONE_MANCALA
        val currentMancalaValue = pockets[ownMancala]

        val features = FloatArray(18)

        for (action in 0 until 6) {
            val absPit = if (isPlayerOne) action else action + 7
            val offset = action * 3

            if (pockets[absPit] == 0) continue

            val stones = pockets[absPit]
            var pos = absPit
            var remaining = stones
            while (remaining > 0) {
                pos = (pos + 1) % Board.TOTAL_POCKETS
                if (pos == skipMancala) continue
                remaining--
            }

            features[offset] = if (pos == ownMancala) 1.0f else 0.0f

            val inOwnTerritory = if (isPlayerOne) pos in 0..5 else pos in 7..12
            if (inOwnTerritory) {
                val pitWasEmpty = pockets[pos] == 0
                val oppositeIdx = 12 - pos
                if (pitWasEmpty && pockets[oppositeIdx] > 0) {
                    features[offset + 1] = (pockets[oppositeIdx] + 1).toFloat() / 48f
                }
            }

            val result = board.playMove(absPit, isPlayerOne)
            features[offset + 2] = (result.board.pockets[ownMancala] - currentMancalaValue).toFloat() / 48f
        }

        return features
    }

    private fun computeChainDepths(board: Board, isPlayerOne: Boolean): FloatArray {
        val mancalaIdx = if (isPlayerOne) Board.PLAYER_ONE_MANCALA else Board.PLAYER_TWO_MANCALA
        val depths = FloatArray(6)

        for (action in 0 until 6) {
            val absPit = if (isPlayerOne) action else action + 7
            if (board.pockets[absPit] == 0) continue

            val firstResult = board.playMove(absPit, isPlayerOne)
            if (!firstResult.endsInMancala) continue

            var count = 1
            var currentBoard = firstResult.board

            while (count < 6) {
                val legalMoves = currentBoard.legalMoves(isPlayerOne)
                if (legalMoves.isEmpty()) break

                var bestMove: Int? = null
                var bestGain = -1

                for (move in legalMoves) {
                    val result = currentBoard.playMove(move, isPlayerOne)
                    if (result.endsInMancala) {
                        val gain = result.board.pockets[mancalaIdx] - currentBoard.pockets[mancalaIdx]
                        if (gain > bestGain) {
                            bestGain = gain
                            bestMove = move
                        }
                    }
                }

                if (bestMove == null) break
                currentBoard = currentBoard.playMove(bestMove, isPlayerOne).board
                count++
            }

            depths[action] = count.toFloat() / 6.0f
        }

        return depths
    }

    private fun matVecMul(vec: FloatArray, mat: Array<FloatArray>, bias: FloatArray): FloatArray {
        val outputDim = bias.size
        val result = bias.copyOf()
        for (j in 0 until outputDim) {
            for (i in vec.indices) {
                result[j] += vec[i] * mat[i][j]
            }
        }
        return result
    }

    private fun reshape(flat: FloatArray, rows: Int, cols: Int): Array<FloatArray> {
        require(flat.size == rows * cols) {
            "Cannot reshape array of size ${flat.size} into ${rows}x${cols}"
        }
        return Array(rows) { r ->
            FloatArray(cols) { c -> flat[r * cols + c] }
        }
    }

    companion object {
        @OptIn(ExperimentalResourceApi::class)
        suspend fun create(searchDepth: Int = 5): NeuralNetEngine {
            val bytes = Res.readBytes(WEIGHTS_RESOURCE_PATH)
            return NeuralNetEngine(parseWeights(bytes), searchDepth)
        }

        suspend fun create(searchDepth: Int, weightBytes: ByteArray): NeuralNetEngine =
            NeuralNetEngine(parseWeights(weightBytes), searchDepth)

        const val WEIGHTS_RESOURCE_PATH = "files/mancala_weights.bin"
    }
}
