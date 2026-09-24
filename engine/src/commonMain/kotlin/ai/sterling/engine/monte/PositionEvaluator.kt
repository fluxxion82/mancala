package ai.sterling.engine.monte

import ai.sterling.engine.monte.test.EvalWeights
import ai.sterling.model.Board
import ai.sterling.model.Game

object PositionEvaluator {
    fun evaluatePosition(game: Game, isPlayerOne: Boolean, weights: EvalWeights): Double {
        var score = 0.0
        val playerPockets = if (isPlayerOne) Board.PLAYER_ONE_POCKETS else Board.PLAYER_TWO_POCKETS
        val opponentPockets = if (isPlayerOne) Board.PLAYER_TWO_POCKETS else Board.PLAYER_ONE_POCKETS
        val playerMancala = if (isPlayerOne) Board.PLAYER_ONE_MANCALA else Board.PLAYER_TWO_MANCALA

        // Priority 1: Immediate scoring moves (highest priority)
        playerPockets.forEach { pos ->
            val stones = game.board.pockets[pos]
            if (stones > 0) {
                val landingPos = if (isPlayerOne) {
                    (pos + stones) % Board.TOTAL_POCKETS
                } else {
                    var landing = pos + stones
                    if (landing >= Board.PLAYER_ONE_MANCALA && landing < Board.PLAYER_TWO_MANCALA) {
                        landing++ // Skip player one's mancala
                    }
                    if (landing >= Board.TOTAL_POCKETS) {
                        landing = landing % Board.TOTAL_POCKETS
                    }
                    landing
                }

                if (landingPos == playerMancala) {
                    score += weights.scoringMove
                    return score // Immediately return - this is always the best move
                }
            }
        }

        // Priority 2: Find best capture opportunity
        var bestCaptureValue = 0
        playerPockets.forEach { pos ->
            val stones = game.board.pockets[pos]
            if (stones > 0) {
                val landingPos = (pos + stones) % Board.TOTAL_POCKETS
                if (landingPos in playerPockets && game.board.pockets[landingPos] == 0) {
                    val oppositePos = Board.TOTAL_POCKETS - 2 - landingPos
                    val captureValue = game.board.pockets[oppositePos]
                    if (captureValue > bestCaptureValue) {
                        bestCaptureValue = captureValue
                    }
                }
            }
        }
        if (bestCaptureValue > 0) {
            score += weights.captureMove + (bestCaptureValue * weights.captureStoneValue)
        }

        // Priority 3: Block opponent's best capture opportunity
        var bestBlockValue = 0
        opponentPockets.forEach { pos ->
            val stones = game.board.pockets[pos]
            if (stones > 0) {
                val landingPos = (pos + stones) % Board.TOTAL_POCKETS
                if (landingPos in opponentPockets && game.board.pockets[landingPos] == 0) {
                    val oppositePos = Board.TOTAL_POCKETS - 2 - landingPos
                    val atRiskStones = game.board.pockets[oppositePos]
                    if (atRiskStones > bestBlockValue) {
                        bestBlockValue = atRiskStones
                    }
                }
            }
        }
        if (bestBlockValue > 0) {
            score -= weights.blockCapture + (bestBlockValue * weights.captureStoneValue)
        }

        // Priority 4: Play rightmost stones
        playerPockets.reversed().forEachIndexed { index, pos ->
            if (game.board.pockets[pos] > 0) {
                score += weights.rightmostBase - (index * weights.rightmostDecay)
                return@forEachIndexed
            }
        }

        return score
    }
}