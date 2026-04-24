package ai.sterling.engine.ml

import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

class NeuralNetEngine(private val searchDepth: Int = 5) {

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
        val npz = NpyReader.loadNpz(
            javaClass.getResourceAsStream("/model/mancala_weights.npz")
                ?: error("Model weights not found in resources")
        )

        version = npz.arrays["version"]?.firstOrNull()?.toInt() ?: 1

        if (version >= 2) {
            activation = "relu"
            val hiddenDim = npz.arrays["hidden_dim"]!!.first().toInt()
            val numBlocks = npz.arrays["num_blocks"]!!.first().toInt()
            inputDim = 56

            // Input projection
            v2ProjW = reshape(npz.arrays["proj_w"]!!, inputDim, hiddenDim)
            v2ProjB = npz.arrays["proj_b"]!!
            v2ProjLnScale = npz.arrays["proj_ln_scale"]!!
            v2ProjLnBias = npz.arrays["proj_ln_bias"]!!

            // Residual blocks
            val blocks = mutableListOf<ResBlock>()
            for (i in 0 until numBlocks) {
                blocks.add(ResBlock(
                    w1 = reshape(npz.arrays["res${i}_w1"]!!, hiddenDim, hiddenDim),
                    b1 = npz.arrays["res${i}_b1"]!!,
                    ln1Scale = npz.arrays["res${i}_ln1_scale"]!!,
                    ln1Bias = npz.arrays["res${i}_ln1_bias"]!!,
                    w2 = reshape(npz.arrays["res${i}_w2"]!!, hiddenDim, hiddenDim),
                    b2 = npz.arrays["res${i}_b2"]!!,
                    ln2Scale = npz.arrays["res${i}_ln2_scale"]!!,
                    ln2Bias = npz.arrays["res${i}_ln2_bias"]!!,
                ))
            }
            v2ResBlocks = blocks

            // Policy head (2-layer)
            v2PolicyW1 = reshape(npz.arrays["policy_w1"]!!, hiddenDim, 256)
            v2PolicyB1 = npz.arrays["policy_b1"]!!
            v2PolicyLnScale = npz.arrays["policy_ln_scale"]!!
            v2PolicyLnBias = npz.arrays["policy_ln_bias"]!!
            v2PolicyW2 = reshape(npz.arrays["policy_w2"]!!, 256, 6)
            v2PolicyB2 = npz.arrays["policy_b2"]!!

            // Value head (2-layer)
            v2ValueW1 = reshape(npz.arrays["value_w1"]!!, hiddenDim, 256)
            v2ValueB1 = npz.arrays["value_b1"]!!
            v2ValueLnScale = npz.arrays["value_ln_scale"]!!
            v2ValueLnBias = npz.arrays["value_ln_bias"]!!
            v2ValueW2 = reshape(npz.arrays["value_w2"]!!, 256, 1)
            v2ValueB2 = npz.arrays["value_b2"]!!

            hasValueHead = true
        } else {
            // V1: plain MLP
            val nLayers = npz.arrays["n_layers"]!!.first().toInt()
            activation = npz.strings["activation"] ?: "tanh"

            val layerList = mutableListOf<Pair<Array<FloatArray>, FloatArray>>()
            for (i in 0 until nLayers) {
                val w = npz.arrays["fe_w$i"]!!
                val b = npz.arrays["fe_b$i"]!!
                val outputDim = b.size
                val inferredInputDim = if (i == 0) w.size / outputDim else layerList[i - 1].second.size
                layerList.add(Pair(reshape(w, inferredInputDim, outputDim), b))
            }
            v1Layers = layerList
            inputDim = layerList[0].first.size

            val lastHiddenSize = layerList.last().second.size
            v1ActionW = reshape(npz.arrays["action_w"]!!, lastHiddenSize, 6)
            v1ActionB = npz.arrays["action_b"]!!

            hasValueHead = npz.arrays.containsKey("value_w") && npz.arrays.containsKey("value_b")

            val vfNLayers = npz.arrays["vf_n_layers"]?.firstOrNull()?.toInt() ?: 0
            if (hasValueHead && vfNLayers > 0) {
                val vfList = mutableListOf<Pair<Array<FloatArray>, FloatArray>>()
                for (i in 0 until vfNLayers) {
                    val w = npz.arrays["vf_w$i"]!!
                    val b = npz.arrays["vf_b$i"]!!
                    val outputDim = b.size
                    val vfInputDim = if (i == 0) inputDim else vfList[i - 1].second.size
                    vfList.add(Pair(reshape(w, vfInputDim, outputDim), b))
                }
                v1ValueLayers = vfList
                val vfHiddenSize = vfList.last().second.size
                v1ValueW = reshape(npz.arrays["value_w"]!!, vfHiddenSize, 1)
                v1ValueB = npz.arrays["value_b"]!!
            } else if (hasValueHead) {
                v1ValueLayers = emptyList()
                v1ValueW = reshape(npz.arrays["value_w"]!!, lastHiddenSize, 1)
                v1ValueB = npz.arrays["value_b"]!!
            } else {
                v1ValueLayers = emptyList()
                v1ValueW = arrayOf(floatArrayOf(0f))
                v1ValueB = floatArrayOf(0f)
            }
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

    private fun selectMoveWithSearch(board: Board, isPlayerOne: Boolean): Int {
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

        val pockets = board.pockets
        val myMancala = if (asPlayerOne) pockets[Board.PLAYER_ONE_MANCALA] else pockets[Board.PLAYER_TWO_MANCALA]
        val oppMancala = if (asPlayerOne) pockets[Board.PLAYER_TWO_MANCALA] else pockets[Board.PLAYER_ONE_MANCALA]
        val mySide = if (asPlayerOne) (0..5).sumOf { pockets[it] } else (7..12).sumOf { pockets[it] }
        val oppSide = if (asPlayerOne) (7..12).sumOf { pockets[it] } else (0..5).sumOf { pockets[it] }
        val heuristicValue = (myMancala - oppMancala + 0.3f * (mySide - oppSide)) / 48f

        return 0.7f * neuralValue + 0.3f * heuristicValue
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

    // ---- Forward pass dispatching ----

    private fun policyForward(input: FloatArray): FloatArray {
        return if (version >= 2) policyForwardV2(input) else policyForwardV1(input)
    }

    private fun valueForward(input: FloatArray): Float {
        return if (version >= 2) valueForwardV2(input) else valueForwardV1(input)
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

    private fun policyForwardV2(input: FloatArray): FloatArray {
        val shared = sharedBodyV2(input)
        // Policy head: Linear -> LN -> ReLU -> Linear
        var p = matVecMul(shared, v2PolicyW1!!, v2PolicyB1!!)
        p = layerNorm(p, v2PolicyLnScale!!, v2PolicyLnBias!!)
        p = relu(p)
        return matVecMul(p, v2PolicyW2!!, v2PolicyB2!!)
    }

    private fun valueForwardV2(input: FloatArray): Float {
        val shared = sharedBodyV2(input)
        // Value head: Linear -> LN -> ReLU -> Linear -> tanh
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
}
