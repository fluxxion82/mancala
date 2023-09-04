import ai.sterling.MainViewModel
import ai.sterling.model.Game
import ai.sterling.ui.MancalaBoard
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
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
                Game.Status.Finished.Draw -> "Draw"
                Game.Status.Finished.PlayerOneWin -> "Player One Wins"
                Game.Status.Finished.PlayerTwoWin -> "Player Two Wins"
                Game.Status.PlayerOneTurn -> "Player One Turn"
                Game.Status.PlayerTwoTurn -> "Player Two Turn"
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
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
        MainViewModel(Game.newGame())
    }

    Window(onCloseRequest = ::exitApplication) {
        println("window")

       // MaterialTheme {
            Column {
                Text("Mancala")

                AppScreen(
                    mainViewModel,
                )
            }
       // }

    }
}

