import ai.sterling.MainViewModel
import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.ui.animation.MancalaController
import ai.sterling.ui.animation.MancalaControllerHost
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.ui.board.BoardLayout
import ai.sterling.ui.board.TurnIndicator
import ai.sterling.ui.theme.BoardColors
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.SharedFlow

private class ViewModelHost(private val vm: MainViewModel) : MancalaControllerHost {
    override fun events(): SharedFlow<MoveEvent> = vm.events
    override fun currentGameStatus(): Game.GameStatus = vm.game.value.status
    override fun currentPockets(): List<Int> = vm.game.value.board.pockets
    override fun currentHumanSide(): HumanSide? = vm.humanSide.value
    override fun applyMove(position: Int) = vm.applyMove(position)
    override suspend fun computeAiMove(): Int = vm.computeAiMove()
}

@Composable
@Preview
fun AppScreen(
    mainViewModel: MainViewModel,
) {
    val host = remember(mainViewModel) { ViewModelHost(mainViewModel) }
    val controller = remember(host) { MancalaController(host) }
    val scope = rememberCoroutineScope()
    val gameStatus by mainViewModel.gameStatus.collectAsState()
    val humanSide by mainViewModel.humanSide.collectAsState()

    LaunchedEffect(controller) {
        controller.runEventLoop(scope)
    }

    val activeMancala = when (gameStatus) {
        Game.GameStatus.PlayerOneTurn -> Board.PLAYER_ONE_MANCALA
        Game.GameStatus.PlayerTwoTurn -> Board.PLAYER_TWO_MANCALA
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BoardColors.TableFelt),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoardLayout(
            controller = controller,
            onPitClick = controller::onPitClick,
            activeMancala = activeMancala,
            humanSide = humanSide,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 25.dp, vertical = 16.dp),
        )

        TurnIndicator(
            status = gameStatus,
            humanSide = humanSide,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NewGameButton(
                text = "New Game — You First",
                onClick = { mainViewModel.restart(HumanSide.PLAYER_ONE) },
            )
            NewGameButton(
                text = "New Game — AI First",
                onClick = { mainViewModel.restart(HumanSide.PLAYER_TWO) },
            )
        }
    }
}

@Composable
private fun NewGameButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = BoardColors.WoodLight,
            contentColor = BoardColors.Parchment,
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

fun main() = application {
    val mainViewModel = remember { MainViewModel() }

    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(size = DpSize(900.dp, 900.dp)),
        title = "Mancala",
    ) {
        AppScreen(mainViewModel)
    }
}
