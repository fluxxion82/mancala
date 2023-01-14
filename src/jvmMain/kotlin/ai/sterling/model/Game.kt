package ai.sterling.model

import java.lang.Double.max
import java.lang.Double.min

class Game (
    val board: Board,
    var status: Status = Status.PlayerOneTurn,
) {
    sealed class Status {
        object PlayerOneTurn : Status()
        object PlayerTwoTurn : Status()

        sealed class Finished : Status() {
            object PlayerOneWin : Finished()
            object PlayerTwoWin : Finished()
            object Draw : Finished()
        }
    }

    fun deepCopy() = Game(board = board.deepCopy(), status = status)

    fun makeMove(position: Int): Status {
        if (status == Status.PlayerOneTurn && position in 7..13 ||
            status == Status.PlayerTwoTurn && position in 0..5 ||
            status is Status.Finished ||
            position == 6 || position == 13) {
            error("Invalid move or status. move: $position, status: $status")
        }

        val lastPocketPlayed = board.playMove(position)
        // eval for winner
        status = if (isGameOver()) {
            board.clearRemainingPockets()
            evalEndStatus()
        } else {
            evalStatus(lastPocketPlayed)
        }

        return status
    }

    fun isGameOver() = board.pockets.subList(0, 6).all { it == 0 } ||
            board.pockets.subList(7, 13).all { it == 0 }

    fun getBoardCurrentPlayer() = if (board.playerOne.turn) 0 else 1

    private fun evalEndStatus(): Status =
        if (board.playerOne.mancala > board.playerTwo.mancala) {
            Status.Finished.PlayerOneWin
        } else if (board.playerTwo.mancala > board.playerOne.mancala) {
            Status.Finished.PlayerTwoWin
        } else {
            assert(board.playerOne.mancala == board.playerTwo.mancala)
            Status.Finished.Draw
        }

    private fun evalStatus(lastPocketPlayed: Int): Status {
        return if (status == Status.PlayerOneTurn && lastPocketPlayed == 6) {
            board.playerOne.turn = true
            board.playerTwo.turn = false
            Status.PlayerOneTurn
        } else if (status == Status.PlayerTwoTurn && lastPocketPlayed == 13) {
            board.playerOne.turn = false
            board.playerTwo.turn = true
            Status.PlayerTwoTurn
        } else if (status == Status.PlayerOneTurn) {
            board.playerOne.turn = false
            board.playerTwo.turn = true
            Status.PlayerTwoTurn
        } else {
            board.playerOne.turn = true
            board.playerTwo.turn = false
            Status.PlayerOneTurn
        }
    }

    fun score(curPlayer: Int, playerTurn: Int, currentMove: Int): Double { //
        val score = if (isGameOver() && curPlayer == playerTurn) {
            100.0
        } else if (isGameOver() && curPlayer != playerTurn) {
            0.0
        } else {
            var startEval = 50.0
            when (curPlayer) {
                0 -> {
                    val emptyPockets = board.pockets.mapIndexed { index, pocket ->
                        if (pocket == 0 && index in 0..5) index else -1
                    }.filter { it != -1 }

                    val opponentMoves = board.legalMoves(false)

                    val canScore = currentMove + board.pockets[currentMove] == 6

                    val canCapture = opponentMoves.any { move ->
                        emptyPockets.contains(12 - board.pockets[move])
                    }

                    if (canScore) {
                        startEval = 90.0
                    } else if (canCapture) {
                        startEval *= .80
                    }

                    if (board.playerOne.mancala > board.playerTwo.mancala) {
                        // increase mancala - good
                        startEval *= .70
                    } else if (board.playerOne.mancala == board.playerTwo.mancala) {
                        startEval *= .60
                    }

                    startEval
                }
                1 -> {
                    val emptyPockets = board.pockets.mapIndexed { index, pocket ->
                        if (pocket == 0 && index in 7..12) index else -1
                    }.filter { it != -1 }

                    val opponentMoves = board.legalMoves(true)

                    val canScore = currentMove + board.pockets[currentMove] == 13

                    val canCapture = opponentMoves.any { move ->
                        emptyPockets.contains(12 - board.pockets[move])
                    }

                    if (canScore) {
                        startEval = 90.0
                    } else if (canCapture) {
                        startEval *= .75
                    }

                    if (board.playerTwo.mancala >= board.playerOne.mancala) {
                        // increase mancala - good
                        startEval *= .70
                    } else if (board.playerTwo.mancala == board.playerTwo.mancala) {
                        startEval *= .60
                    }

                    startEval
                }

                else -> error("only two players")
            }

//            if (curPlayer == 0) {
//                final.board.playerOne.mancala.toDouble() - final.board.playerTwo.mancala.toDouble()
//            } else if (curPlayer == 1) {
//                final.board.playerTwo.mancala.toDouble() - final.board.playerOne.mancala.toDouble()
//            } else {
//                50.0
//            }
        }

        println("curMove: $currentMove, score: $score")

        return score
    }

    fun findBestMove(board: Board, playerOne: Boolean, depth: Int, alpha: Double, beta: Double): Pair<Int, Double> {
        var alpha = alpha
        val legalMoves = board.legalMoves(playerOne)
        if (depth == 0 || legalMoves.isEmpty()) {
            return -1 to score(
                if (playerOne) 0 else 1,
                if (playerOne) 0 else 1,
                board.lastMovePlayed
            )
        }
        var bestMove = -1 to 0.0
        var bestValue = if (playerOne) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        for (move in legalMoves) {
            val newBoard = board.deepCopy()
            newBoard.playMove(move)
            var (_, value) = findBestMove(
                newBoard, newBoard.playerOne.turn, depth - 1, -beta, -alpha)
            value = -value
            if (value > bestValue) {
                bestValue = value
                bestMove = move to value
            }
            alpha = max(alpha, bestValue)
            if (alpha >= beta) {
                break
            }
        }
        return bestMove
    }

    fun alphaBetaMove(ply: Int): Pair<Int, Double> {
        var move = -1
        var alpha = Double.NEGATIVE_INFINITY
        val beta = Double.NEGATIVE_INFINITY
        var score = Double.NEGATIVE_INFINITY
        var currentMoveScore = 0.0
        val curPlayer = getBoardCurrentPlayer()
        board.legalMoves(getBoardCurrentPlayer() == 0).forEach { legalMove ->
            if (ply == 0) {
                return legalMove to score(curPlayer, curPlayer, legalMove)
            }
            if (isGameOver()) {
                return move to 0.0
            }

            val gameCopy = deepCopy()
            val player = if (curPlayer == 0) 1 else 0

            val curScore = gameCopy.score(player, gameCopy.getBoardCurrentPlayer(), legalMove)
            if (curScore > currentMoveScore) {
                currentMoveScore = curScore
                move = legalMove
                score = curScore
            }

            gameCopy.makeMove(legalMove)
            val abValue = minABValue(gameCopy, ply -1, player, alpha, beta)
            if (abValue > score) {
                move = legalMove
                score = abValue
            }
            alpha = max(score, alpha)
        }

        return move to score
    }

    private fun minABValue(game: Game, ply: Int, player: Int, alpha: Double, beta: Double): Double {
        if (game.isGameOver()) {
            return game.score(player, game.getBoardCurrentPlayer(), 0)
        } // todo not sure we need this
        var score = Double.POSITIVE_INFINITY
        var newBeta = beta
        game.board.legalMoves(game.getBoardCurrentPlayer() == 0).forEach {  legalMove ->
            if (ply == 0) {
                return game.score(player, game.getBoardCurrentPlayer(), legalMove)
            }
            val gameCopy = game.deepCopy()
            gameCopy.makeMove(legalMove)
            score = min(score, maxABValue(gameCopy, ply-1, if (player == 0) 1 else 0, alpha, newBeta))
            if (score <= alpha) {
                return score
            }
            newBeta = min(newBeta, score)
        }

        return score
    }

    private fun maxABValue(game: Game, ply: Int, player: Int, alpha: Double, beta: Double): Double {
        if (game.isGameOver()) {
            return game.score(player, game.getBoardCurrentPlayer(),0)
        } // todo not sure we need this
        var score = Double.NEGATIVE_INFINITY
        var newAlpha = alpha
        game.board.legalMoves(game.getBoardCurrentPlayer() == 0).forEach {  legalMove ->
            if (ply == 0) {
                return score(player, game.getBoardCurrentPlayer(), legalMove)
            }
            val gameCopy = game.deepCopy()
            gameCopy.makeMove(legalMove)
            score = max(score, minABValue(gameCopy, ply-1, if (player == 0) 1 else 0, newAlpha, beta))
            if (score >= beta) {
                return score
            }
            newAlpha = max(newAlpha, score)
        }

        return score
    }

    companion object {
        fun newGame() : Game {
            return Game(
                board = Board(
                    playerOne = Player(0, true),
                    playerTwo = Player(0, false),
                    pockets = mutableListOf(
                        4,4,4,4,4,4,0,4,4,4,4,4,4,0
                    )
                ),
            )
        }

        fun newGameWithPosition(pockets: MutableList<Int>, playerOneTurn: Boolean) : Game {
            return Game(
                board = Board(
                    playerOne = Player(pockets[6], playerOneTurn),
                    playerTwo = Player(pockets[13], !playerOneTurn),
                    pockets = pockets
                ),
            ).also {
                // player one set at init...will try to switch when calling with last pocket of 0 since not checking
                // pocket value. last pocket value only used with scoring
                if (!playerOneTurn) {
                    it.status = it.evalStatus(7) // todo initialize game status better
                }
            }
        }
    }
}
