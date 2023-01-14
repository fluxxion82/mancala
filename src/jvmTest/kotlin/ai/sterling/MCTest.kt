package ai.sterling

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import org.junit.Test

class MCTest {

    @Test
    fun eval() {
        val game = Game.newGame()
        game.makeMove(2)
        game.makeMove(5)


        val engine = MonteCarlo(game)
        val move = engine.runBest(20)

        println("move: $move")
        game.makeMove(move)
        game.board.printBoard()
    }
}