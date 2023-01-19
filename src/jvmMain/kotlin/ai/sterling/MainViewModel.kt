package ai.sterling

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext

class MainViewModel(
    private val game: Game
): BaseViewModel() {
    private val mcEngine = MonteCarlo(game)

    val stones = mutableStateListOf<Int>().apply { addAll(game.board.pockets) } // MutableStateFlow(game.board.pockets)
    val gameStatus = mutableStateOf(game.status)

    fun onMoveInput(position: Int) {
        println("move input")
        if (gameStatus.value is Game.Status.Finished) {
            return
        }

        launch {
            gameStatus.value = game.makeMove(position)
            mcEngine.apply(game)
            stones.clear()
            stones.addAll(game.board.pockets)

            game.board.printBoard()
            val status = gameStatus.value
            println("your move: $position, stats=$status")


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
    }

    private fun makeComputerMove() {
        println("makeComputerMove")
        launch {
            delay(2000)
            while (game.board.playerTwo.turn && gameStatus.value !is Game.Status.Finished) {
                val nextMove = mcEngine.runBest(20) // game.alphaBetaMove(5)
                gameStatus.value = game.makeMove(nextMove)
                mcEngine.apply(game)
                stones.clear()
                stones.addAll(game.board.pockets)

                val status = gameStatus.value
                if (status is Game.Status.Finished) {
                    when (status) {
                        is Game.Status.Finished.Draw -> println("draw")
                        is Game.Status.Finished.PlayerOneWin -> println("player one win")
                        is Game.Status.Finished.PlayerTwoWin -> println("player two win")
                    }
                }

                game.board.printBoard()
                println("computer move: $nextMove")
            }
        }
    }
}

//private const val JOB_KEY = "moe.tlaster.precompose.viewmodel.ViewModelCoroutineScope.JOB_KEY"
//val ViewModel.viewModelScope: CoroutineScope
//    get() {
//        val scope: CoroutineScope? = getTag(JOB_KEY)
//        if (scope != null) {
//            return scope
//        }
//        return setTagIfAbsent(
//            JOB_KEY,
//            CloseableCoroutineScope(SupervisorJob() + Dispatchers.Main)
//        )
//    }
//
//internal class CloseableCoroutineScope(context: CoroutineContext) : Disposable, CoroutineScope {
//    override val coroutineContext: CoroutineContext = context
//
//    override fun dispose() {
//        coroutineContext.cancel()
//    }
//}
