package ai.sterling.model

import kotlin.math.abs

class Game (
    val board: Board,
    var status: Status = Status.PlayerOneTurn,
) {
    sealed class Status {
        data object PlayerOneTurn : Status()
        data object PlayerTwoTurn : Status()

        sealed class Finished : Status() {
            data object PlayerOneWin : Finished()
            data object PlayerTwoWin : Finished()
            data object Draw : Finished()
        }
    }

    fun deepCopy() = Game(board = board.deepCopy(), status = status)

    fun makeMove(position: Int): Status {
        if (status == Status.PlayerOneTurn && position in 7..13 ||
            status == Status.PlayerTwoTurn && position in 0..5 ||
            status is Status.Finished ||
            position == 6 || position == 13 ||
            board.pockets[position] == 0) {
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

    fun score(curPlayer: Int): Double {
        if (isGameOver()) {
            // Game over conditions
            return when {
                curPlayer == 0 && board.playerOne.mancala < board.playerTwo.mancala ||
                        curPlayer == 1 && board.playerTwo.mancala < board.playerOne.mancala -> -9000.0
                curPlayer == 0 && board.playerOne.mancala > board.playerTwo.mancala ||
                        curPlayer == 1 && board.playerTwo.mancala > board.playerOne.mancala -> 9000.0
                board.playerOne.mancala == board.playerTwo.mancala -> 1000.0
                else -> 0.0
            }
        }

        var score = 50.0
        val myMancala = if (curPlayer == 0) board.playerOne.mancala else board.playerTwo.mancala
        val opponentMancala = if (curPlayer == 0) board.playerTwo.mancala else board.playerOne.mancala
        score += 20 * (myMancala - opponentMancala)

        // Adjusting strategy based on ability to score, capture, and defense mechanisms
        val mySide =  board.pockets // if (curPlayer == 0) board.pockets.subList(0, 6)  else board.pockets.subList(7,12)
        val opponentSide = board.pockets // if (curPlayer == 0) board.pockets.subList(7,12) else board.pockets.subList(0,6)
        val myLegalMoves = board.legalMoves(curPlayer == 0)

        // Priority 1: Can score another turn
        myLegalMoves.forEach { move ->
            val endingPocketIndex = (move + mySide[move]) % 14
            if (endingPocketIndex == myMancala) {
                score += 3000.0 // High score for getting another turn
            }
        }

        // Priority 2: Can capture opponent's stones
        var maxCapture = 0
        myLegalMoves.forEach { move ->
            val stonesToMove = mySide[move]
            val landingIndex = move + stonesToMove
            if (landingIndex < 6 && mySide[landingIndex] == 0 && opponentSide[5 - landingIndex] > 0) { // Assumes index 0-5 for pockets
                maxCapture = maxOf(maxCapture, opponentSide[5 - landingIndex])
            }
        }
        if (maxCapture > 0) score += 2000.0 + maxCapture // Value capturing moves, adding the number of stones captured as a bonus

        // Priority 3: Defense against opponent's potential capture
        val opponentLegalMoves = board.legalMoves(curPlayer != 0)
        var maxOpponentCapture = 0
        opponentLegalMoves.forEach { move ->
            val stonesToMove = opponentSide[move]
            val landingIndex = move + stonesToMove
            if (landingIndex < 6 && opponentSide[landingIndex] == 0 && mySide[5 - landingIndex] > 0) {
                maxOpponentCapture = maxOf(maxOpponentCapture, mySide[5 - landingIndex])
            }
        }
        if (maxOpponentCapture > 0) score -= 1500.0 + maxOpponentCapture // Penalize positions where the opponent can capture

        // Priority 4: Set up for combo scores
        if (mySide[4] == 1) { // Assuming index 4 is the second-to-right pocket
            score += 1000.0 // Bonus for setting up a position to score twice in the next turn
        }

        // Priority 5: Clear out right-most pockets
        if (mySide[5] > 0) { // Assuming index 5 is the right-most pocket
            score += 500.0 // Bonus for having stones in the right-most pocket to clear it out
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
