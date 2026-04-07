package ai.sterling.engine.ml

import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

class NeuralNetEngine(private val searchDepth: Int = 5) {

    private val layers: List<Pair<Array<FloatArray>, FloatArray>>
    private val actionW: Array<FloatArray>
    private val actionB: FloatArray
    private val valueLayers: List<Pair<Array<FloatArray>, FloatArray>>
    private val valueW: Array<FloatArray>
    private val valueB: FloatArray
    private val activation: String
    private val inputDim: Int
    private val hasValueHead: Boolean

    init {
        val npz = NpyReader.loadNpz(
            javaClass.getResourceAsStream("/model/mancala_weights.npz")
                ?: error("Model weights not found in resources")
        )

        val nLayers = npz.arrays["n_layers"]!!.first().toInt()
        activation = npz.strings["activation"] ?: "tanh"

        // Policy network
        val layerList = mutableListOf<Pair<Array<FloatArray>, FloatArray>>()
        for (i in 0 until nLayers) {
            val w = npz.arrays["fe_w$i"]!!
            val b = npz.arrays["fe_b$i"]!!
            val outputDim = b.size
            val inferredInputDim = if (i == 0) w.size / outputDim else layerList[i - 1].second.size
            layerList.add(Pair(reshape(w, inferredInputDim, outputDim), b))
        }
        layers = layerList
        inputDim = layers[0].first.size

        val lastHiddenSize = layers.last().second.size
        actionW = reshape(npz.arrays["action_w"]!!, lastHiddenSize, 6)
        actionB = npz.arrays["action_b"]!!

        // Value network — two formats:
        // PPO: separate vf_w0/b0..vf_wN/bN layers + value_w/value_b
        // AlphaZero: shared body (same fe_w layers) + value_w/value_b only
        hasValueHead = npz.arrays.containsKey("value_w") && npz.arrays.containsKey("value_b")

        val vfNLayers = npz.arrays["vf_n_layers"]?.firstOrNull()?.toInt() ?: 0
        if (hasValueHead && vfNLayers > 0) {
            // PPO model: separate value layers
            val vfList = mutableListOf<Pair<Array<FloatArray>, FloatArray>>()
            for (i in 0 until vfNLayers) {
                val w = npz.arrays["vf_w$i"]!!
                val b = npz.arrays["vf_b$i"]!!
                val outputDim = b.size
                val vfInputDim = if (i == 0) inputDim else vfList[i - 1].second.size
                vfList.add(Pair(reshape(w, vfInputDim, outputDim), b))
            }
            valueLayers = vfList
            val vfHiddenSize = valueLayers.last().second.size
            valueW = reshape(npz.arrays["value_w"]!!, vfHiddenSize, 1)
            valueB = npz.arrays["value_b"]!!
        } else if (hasValueHead) {
            // AlphaZero model: shared body, value head applied to policy body output
            valueLayers = emptyList()  // empty = use policy layers (shared body)
            valueW = reshape(npz.arrays["value_w"]!!, lastHiddenSize, 1)
            valueB = npz.arrays["value_b"]!!
        } else {
            valueLayers = emptyList()
            valueW = arrayOf(floatArrayOf(0f))
            valueB = floatArrayOf(0f)
        }
    }

    fun selectMove(game: Game): Int {
        val isPlayerOne = game.status == GameStatus.PlayerOneTurn

        return if (hasValueHead && searchDepth > 0) {
            selectMoveWithSearch(game.board, isPlayerOne)
        } else {
            selectMovePolicy(game.board, isPlayerOne)
        }
    }

    /** Original policy-only move selection (no search). */
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

    /** Minimax search with neural value evaluation and policy move ordering. */
    private fun selectMoveWithSearch(board: Board, isPlayerOne: Boolean): Int {
        val legalMoves = board.legalMoves(isPlayerOne)
        if (legalMoves.isEmpty()) return 0

        // Get policy logits for move ordering
        val obs = buildObs(board, isPlayerOne)
        val logits = policyForward(obs)

        // Convert to relative actions (0-5) and sort by policy preference
        val relativeActions = if (isPlayerOne) {
            legalMoves.sortedByDescending { logits[it] }
        } else {
            legalMoves.sortedByDescending { logits[it - 7] }
        }

        var bestMove = relativeActions[0]
        var bestScore = Float.NEGATIVE_INFINITY

        for (move in relativeActions) {
            val result = board.playMove(move, isPlayerOne)
            val score = if (result.board.isGameOver()) {
                evaluateBoard(result.board, isPlayerOne)
            } else if (result.endsInMancala) {
                minimax(result.board, searchDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, isPlayerOne, true, isPlayerOne)
            } else {
                minimax(result.board, searchDepth - 1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, !isPlayerOne, false, isPlayerOne)
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

        // Move ordering using policy logits
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

    /** Evaluate a board position using blended neural + heuristic evaluation. */
    private fun evaluateBoard(board: Board, asPlayerOne: Boolean): Float {
        val obs = buildObs(board, asPlayerOne)
        val neuralValue = valueForward(obs)

        // Heuristic: score differential + 0.3 * side stone differential (matches PPO shaping)
        val pockets = board.pockets
        val myMancala = if (asPlayerOne) pockets[Board.PLAYER_ONE_MANCALA] else pockets[Board.PLAYER_TWO_MANCALA]
        val oppMancala = if (asPlayerOne) pockets[Board.PLAYER_TWO_MANCALA] else pockets[Board.PLAYER_ONE_MANCALA]
        val mySide = if (asPlayerOne) (0..5).sumOf { pockets[it] } else (7..12).sumOf { pockets[it] }
        val oppSide = if (asPlayerOne) (7..12).sumOf { pockets[it] } else (0..5).sumOf { pockets[it] }
        val heuristicValue = (myMancala - oppMancala + 0.3f * (mySide - oppSide)) / 48f

        return 0.7f * neuralValue + 0.3f * heuristicValue
    }

    /** Evaluate a terminal board (game over, all stones collected). */
    private fun evaluateTerminal(board: Board, asPlayerOne: Boolean): Float {
        val myMancala = if (asPlayerOne) board.pockets[Board.PLAYER_ONE_MANCALA] else board.pockets[Board.PLAYER_TWO_MANCALA]
        val oppMancala = if (asPlayerOne) board.pockets[Board.PLAYER_TWO_MANCALA] else board.pockets[Board.PLAYER_ONE_MANCALA]
        // Strong signal for terminal states: normalized score difference
        return (myMancala - oppMancala).toFloat() / 48f
    }

    /** Build the observation vector for a given board and player perspective. */
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
                pockets[6].toFloat() / 48f
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

    /** Forward pass through policy network, returns action logits. */
    private fun policyForward(input: FloatArray): FloatArray {
        var x = input
        for ((w, b) in layers) {
            x = matVecMul(x, w, b)
            x = activate(x)
        }
        return matVecMul(x, actionW, actionB)
    }

    /** Forward pass through value network, returns scalar value estimate. */
    private fun valueForward(input: FloatArray): Float {
        var x = input
        // If valueLayers is empty, use the shared policy body (AlphaZero architecture)
        val bodyLayers = if (valueLayers.isEmpty()) layers else valueLayers
        for ((w, b) in bodyLayers) {
            x = matVecMul(x, w, b)
            x = activate(x)
        }
        val out = matVecMul(x, valueW, valueB)
        return out[0]
    }

    private fun activate(x: FloatArray): FloatArray = when (activation) {
        "relu" -> FloatArray(x.size) { max(0f, x[it]) }
        else -> FloatArray(x.size) { tanh(x[it].toDouble()).toFloat() }
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
}
