package ai.sterling.model

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

        assert(board.pockets.sumOf { it } == 48)
        return status
    }

    private fun isGameOver() = board.pockets.subList(0, 6).all { it == 0 } ||
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

    fun score(curPlayer: Int, playerTurn: Int, currentMove: Int): Double {
        if (isGameOver()) {
            return when {
                curPlayer == playerTurn -> 1000.0
                else -> 0.0
            }
        }

        var score = 50.0
        val myMancala = if (curPlayer == 0) board.playerOne.mancala else board.playerTwo.mancala
        val opponentMancala = if (curPlayer == 0) board.playerTwo.mancala else board.playerOne.mancala
        score += 10 * (myMancala - opponentMancala)

        val getsNewTurn = curPlayer == playerTurn
        if (getsNewTurn) {
            score += 800
            // Get the legal moves for the next turn
            val nextLegalMoves = board.legalMoves(playerTurn == 0)

            // Check if the player can score again in the next turn
            val newTurnPocket = if (playerTurn == 0) 6 else 13
            val canScore = nextLegalMoves.any { move ->
                (move + board.pockets[move]) % 14 == newTurnPocket
            }
            if (canScore) {
                score += 500
            }

            // Check if the player can capture in the next turn
//            val canCapture = nextLegalMoves.any { move ->
//                val oppositePocket = 12 - move
//                nextGameState.board.pockets[move] == 1 && nextGameState.board.pockets[oppositePocket] > 0
//            }
//            if (canCapture) {
//                score += 200
//            }
        } else {
            val newTurnPocket = if (playerTurn == 0) 6 else 13

            val opponentCanScore = board.legalMoves(playerTurn == 0).any { move ->
                (move + board.pockets[move]) % 14 == newTurnPocket
            }
            if (opponentCanScore) {
                score -= 400
            }
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
                    ),
                ),
            )
        }

        fun newGameWithPosition(pockets: MutableList<Int>, playerOneTurn: Boolean) : Game {
            return Game(
                board = Board(
                    playerOne = Player(pockets[6], playerOneTurn),
                    playerTwo = Player(pockets[13], !playerOneTurn),
                    pockets = pockets,
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
