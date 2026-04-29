import ai.sterling.MancalaGame
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = remember { WindowState(size = DpSize(1000.dp, 900.dp)) },
        title = "Mancala",
    ) {
        MancalaGame()
    }
}
