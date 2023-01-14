package ai.sterling

import ai.sterling.model.Game
import kotlin.test.Test
import kotlin.test.assertTrue

class GameTest {

    @Test
    fun simpleTurn() {
        val game = Game.newGame()
        game.makeMove(2)


        assertTrue {
            game.board.pockets[0] == 4 && game.board.pockets[1] == 4 &&
                    game.board.pockets[2] == 0 && game.board.pockets[3] == 5 &&
                    game.board.pockets[4] == 5 && game.board.pockets[5] == 5 &&
                    game.board.pockets[6] == 1 && game.board.pockets[7] == 4 &&
                    game.board.pockets[8] == 4 && game.board.pockets[9] == 4 &&
                    game.board.pockets[10] == 4 && game.board.pockets[11] == 4 &&
                    game.board.pockets[12] == 4 && game.board.pockets[13] == 0
        }

        game.makeMove(5)

        assertTrue {
            game.board.pockets[0] == 4 && game.board.pockets[1] == 4 &&
                    game.board.pockets[2] == 0 && game.board.pockets[3] == 5 &&
                    game.board.pockets[4] == 5 && game.board.pockets[5] == 0 &&
                    game.board.pockets[6] == 2 && game.board.pockets[7] == 5 &&
                    game.board.pockets[8] == 5 && game.board.pockets[9] == 5 &&
                    game.board.pockets[10] == 5 && game.board.pockets[11] == 4 &&
                    game.board.pockets[12] == 4 && game.board.pockets[13] == 0
        }

        assertTrue {
            !game.board.playerOne.turn && game.board.playerTwo.turn && game.status == Game.Status.PlayerTwoTurn
        }
    }

    @Test
    fun alphaBeta() {
        val game = Game.newGame()
        game.makeMove(2)
        game.makeMove(5)

        var alphaBeta = game.alphaBetaMove(4)
        println("move: ${alphaBeta.first}, score: ${alphaBeta.second}")

        game.board.printBoard()

        game.makeMove(8)

        game.board.printBoard()

        alphaBeta = game.alphaBetaMove(4)
        println("move: ${alphaBeta.first}, score: ${alphaBeta.second}")
    }

    @Test
    fun alphaBetaPruning() {
        val game = Game.newGame()
        game.makeMove(2)
        game.makeMove(5)

        var alphaBeta = game.alphaBetaMove(4)
            // game.findBestMove(game.board, game.board.playerOne.turn, 4, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY) // alphaBeta(game, 5, player = if (game.board.playerOne.turn) 0 else 1)
        println("move: ${alphaBeta.first}, score: ${alphaBeta.second}")

        game.board.printBoard()

        game.makeMove(7)

        game.board.printBoard()

        alphaBeta = game.alphaBetaMove(4)
            // game.findBestMove(game.board, game.board.playerOne.turn, 4, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY) // alphaBeta(game, 5, player = if (game.board.playerOne.turn) 0 else 1) // game.alphaBetaMove(4)
        println("move: ${alphaBeta.first}, score: ${alphaBeta.second}")
    }

    @Test
    fun captureTest() {
        val game = Game.newGame()
        game.makeMove(2)
        game.makeMove(5)

        // opponent
        game.makeMove(8)
        game.makeMove(7)

        game.makeMove(4)

        // opponent
        game.makeMove(8)

        game.makeMove(5)
        game.makeMove(3)

        // opponent
        game.makeMove(7)

        game.makeMove(5)
        game.makeMove(1)

        // opponent
        game.makeMove(8)

        game.makeMove(5)
        game.makeMove(4)
        game.makeMove(5)
        game.makeMove(0)

        // opponent
        game.makeMove(11)

        game.makeMove(3)
        game.makeMove(5)
        game.makeMove(4)
        game.makeMove(5)

        game.board.printBoard()

        game.makeMove(1)

        game.board.printBoard()

        assertTrue {
            game.board.pockets[0] == 1 && game.board.pockets[1] == 0 &&
                    game.board.pockets[2] == 4 && game.board.pockets[3] == 0 &&
                    game.board.pockets[4] == 0 && game.board.pockets[5] == 0 &&
                    game.board.pockets[6] == 25 && game.board.pockets[7] == 0 &&
                    game.board.pockets[8] == 0 && game.board.pockets[9] == 0 &&
                    game.board.pockets[10] == 9 && game.board.pockets[11] == 0 &&
                    game.board.pockets[12] == 7 && game.board.pockets[13] == 2
        }

        assertTrue {
            !game.board.playerOne.turn && game.board.playerTwo.turn && game.status == Game.Status.PlayerTwoTurn
        }
    }

    @Test
    fun computerWrap() {
        /**
         * 9 | 9 | 0 | 0 | 0 | 0
         * 3                   15
         * 1 | 2 | 3 | 2 | 0 | 0
         * your move: 3
         * 9 | 9 | 0 | 0 | 0 | 0
         * 3                   15
         * 1 | 2 | 3 | 0 | 1 | 1
         * computer move: (11, 100.0)
         * 10 | 0 | 0 | 0 | 0 | 0
         * 3                   15
         * 2 | 3 | 4 | 1 | 2 | 0
         */
        val game = Game.newGameWithPosition(
            mutableListOf(
                1,2,3,2,0,0,15,0,0,0,0,9,9,3
            ),
            true
        )

        game.makeMove(3)

        game.board.printBoard()

        game.makeMove(11)

        game.board.printBoard()
    }

    @Test
    fun lastComputerMove() {
        val game = Game.newGameWithPosition(
            mutableListOf(
                1,2,3,2,0,0,15,0,0,0,0,9,9,3
            ),
            true
        )

        game.makeMove(3)

        // opponent
        game.makeMove(11)

        game.makeMove(4)
        game.makeMove(5)
        game.makeMove(2)
        game.makeMove(5)
        game.makeMove(3)

        val move = game.alphaBetaMove(4)
        // opponent
        game.makeMove(move.first)
        game.board.printBoard()
    }

    @Test
    fun endGame() {
        val game = Game.newGameWithPosition(
            mutableListOf(
                0,0,2,1,1,0,25,0,0,0,0,0,1,15
            ),
            false
        )

        game.board.printBoard()

        assertTrue {
            !game.board.playerOne.turn && game.board.playerTwo.turn && game.status == Game.Status.PlayerTwoTurn
        }

        val move = game.alphaBetaMove(4)
        // opponent
        val status = game.makeMove(move.first)
        game.board.printBoard()

        assertTrue {
            status == Game.Status.Finished.PlayerOneWin
        }
    }
}
