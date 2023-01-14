package ai.sterling.engine

import ai.sterling.model.Game
import java.lang.Double.max
import java.lang.Double.min

fun alphaBeta(
    game: Game,
    ply: Int,
    alpha: Double = Double.NEGATIVE_INFINITY,
    beta: Double = Double.NEGATIVE_INFINITY,
    player: Int
): Pair<Int, Double> {
    var move = -1
    var alpha = alpha
    var beta = beta
    var score = Double.NEGATIVE_INFINITY
    if (player == 0) {
        game.board.legalMoves(true).forEach { legalMove ->
            if (ply == 0) {
                return legalMove to game.score(player, 0, legalMove)
            }
            if (game.isGameOver()) {
                return move to 0.0
            }

            val gameCopy = game.deepCopy()
            gameCopy.makeMove(legalMove)

            score = max(
                score,
                alphaBeta(
                    gameCopy,
                    ply - 1,
                    alpha,
                    beta,
                    if (gameCopy.status == Game.Status.PlayerOneTurn) 0 else 1
                ).second
            )
            if (score > beta) {
                move = legalMove
                return legalMove to score
            }
            alpha = max(alpha, score)
        }
    } else {
        score = Double.POSITIVE_INFINITY
        game.board.legalMoves(false).forEach { legalMove ->
            if (ply == 0) {
                return legalMove to game.score(player, 1, legalMove)
            }
            if (game.isGameOver()) {
                return move to 0.0
            }

            val gameCopy = game.deepCopy()
            gameCopy.makeMove(legalMove)

            score = min(score,
                alphaBeta(
                    gameCopy,
                    ply - 1,
                    alpha,
                    beta,
                    if (gameCopy.status == Game.Status.PlayerOneTurn) 0 else 1
                ).second
            )
            if (score <= alpha) {
                move = legalMove
                return legalMove to score
            }
            beta = min(beta, score)
        }
    }

    return move to score
}
