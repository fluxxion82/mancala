import ai.sterling.MainViewModel
import ai.sterling.model.Game
import ai.sterling.ui.MancalaBoard
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

@Composable
@Preview
fun AppScreen(
    mainViewModel: MainViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        MancalaBoard(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(25.dp),
            stones =  mainViewModel.stones,
        ) {
            println("click: $it")
            mainViewModel.onMoveInput(it)
        }

        Text(
            text = when (mainViewModel.gameStatus.value) {
                Game.GameStatus.Finished.Draw -> "Draw"
                Game.GameStatus.Finished.PlayerOneWin -> "Player One Wins"
                Game.GameStatus.Finished.PlayerTwoWin -> "Player Two Wins"
                Game.GameStatus.PlayerOneTurn -> "Player One Turn"
                Game.GameStatus.PlayerTwoTurn -> "Player Two Turn"
            },
            modifier = when (mainViewModel.gameStatus.value) {
                Game.GameStatus.PlayerOneTurn -> Modifier.align(Alignment.CenterHorizontally).background(Color.Green)
                Game.GameStatus.PlayerTwoTurn -> Modifier.align(Alignment.CenterHorizontally).background(Color.Red)
                else -> Modifier.align(Alignment.CenterHorizontally)
            },
            fontSize = 18.sp,
            fontStyle = FontStyle.Normal,
            fontWeight = FontWeight.Bold
        )

        Button(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = mainViewModel::onRestartClick
        ) {
            Text(text = "New Game")
        }
    }
}

fun main() = application {
    println("main")

    val mainViewModel = remember {
        MainViewModel()
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(size = DpSize(900.dp, 900.dp)),
    ) {
        println("window")

        Column {
            Text("Mancala")

            AppScreen(
                mainViewModel,
            )
        }
    }
}

