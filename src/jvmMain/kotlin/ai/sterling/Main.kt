import ai.sterling.MainViewModel
import ai.sterling.model.Game
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

@Composable
@Preview
fun AppScreen(
    mainViewModel: MainViewModel
) {
    var text by remember { mutableStateOf("") }
    var textValue by rememberSaveable { mutableStateOf(text) }


    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            label = { Text("Enter Move") },
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
            },
        )

        Button(onClick = {
            mainViewModel.onMoveInput(textValue.toInt())
            textValue = ""
        }) {
            Text("Make Move")
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

        MaterialTheme {
            Column {
                Text("Mancala")

                AppScreen(mainViewModel)
            }
        }

    }
}

