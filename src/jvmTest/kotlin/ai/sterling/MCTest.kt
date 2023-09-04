package ai.sterling

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MCTest {

    @Test
    fun eval() {
        val game = Game.newGame()
        game.makeMove(2)
        game.makeMove(5)


        val engine = MonteCarlo(game)
        val move = engine.runBest(100)

        println("move: $move")
        assertEquals(8, move)
        game.makeMove(move)
        game.board.printBoard()
    }

    @Test
    fun testImmediateScoring() {
        // Initialize game state where an immediate scoring opportunity exists
        val initialPockets = mutableListOf(4, 4, 4, 4, 4, 4, 0, 4, 4, 4, 4, 4, 4, 0)
        val game = Game.newGameWithPosition(initialPockets, true)

        // Initialize MonteCarlo engine
        val monteCarlo = MonteCarlo(game)

        // Run Monte Carlo to get the best move
        val bestMove = monteCarlo.runBest(1000)  // Run 1000 simulations for example

        // Assert - replace `expectedBestMove` with the move that should lead to immediate scoring
        val expectedBestMoves = listOf(2, 5, 8)
        assertTrue(expectedBestMoves.contains(bestMove), "The best move,$bestMove, should lead to immediate scoring")
    }
}