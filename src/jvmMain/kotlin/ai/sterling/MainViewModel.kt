package ai.sterling

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(
    private val game: Game
) {
    val mcEngine = MonteCarlo(game)

    init {
        game.makeMove(2)
        game.makeMove(5)
        game.board.printBoard()

        makeComputerMove()
    }

    fun onMoveInput(position: Int) {
        val status = game.makeMove(position)
        mcEngine.apply(game)
        println("your move: $position, stats=$status")
        game.board.printBoard()

        if (status is Game.Status.Finished) {
            when (status) {
                is Game.Status.Finished.Draw -> println("draw")
                is Game.Status.Finished.PlayerOneWin -> println("player one win")
                is Game.Status.Finished.PlayerTwoWin -> println("player two win")
            }
        } else {
            makeComputerMove()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun makeComputerMove() {
        GlobalScope.launch {
            delay(2000)
            while (game.board.playerTwo.turn) {
                val nextMove = mcEngine.runBest(20) // game.alphaBetaMove(5)
                println("computer move: $nextMove")
                val status = game.makeMove(nextMove)
                if (status is Game.Status.Finished) {
                    when (status) {
                        is Game.Status.Finished.Draw -> println("draw")
                        is Game.Status.Finished.PlayerOneWin -> println("player one win")
                        is Game.Status.Finished.PlayerTwoWin -> println("player two win")
                    }
                }

                mcEngine.apply(game)
                game.board.printBoard()
            }
        }
    }
}