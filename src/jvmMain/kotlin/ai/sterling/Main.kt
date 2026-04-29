import ai.sterling.data.InMemoryGameRepository
import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.ui.animation.MancalaBoardAnimationState
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.ui.board.BoardLayout
import ai.sterling.ui.board.TurnIndicator
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import ai.sterling.viewmodel.MancalaBoardViewModel
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
@Preview
fun AppScreen(
    viewModel: MancalaBoardViewModel,
) {
    val animationState = remember { MancalaBoardAnimationState() }
    val humanSide by viewModel.humanSide.collectAsState()

    // Glow follows what's *visible* on the board, not the live game truth — so the
    // mancala glow stays put during a player's animation and only flips once the
    // animation finishes. During the AI think-delay it sits on the AI's mancala
    // (the side that's about to move), which reads as "AI is thinking."
    var displayedStatus by remember { mutableStateOf<Game.GameStatus>(Game.GameStatus.PlayerOneTurn) }

    LaunchedEffect(viewModel, animationState) {
        // Wait for first layout pass so all 14 pit centers are populated.
        snapshotFlow { animationState.pitCenters.size == Board.TOTAL_POCKETS }.first { it }

        viewModel.events.collect { event ->
            when (event) {
                MoveEvent.Reset -> {
                    animationState.handleReset()
                    displayedStatus = Game.GameStatus.PlayerOneTurn
                }
                is MoveEvent.MoveApplied -> {
                    val side = viewModel.humanSide.value
                    val isAiMove = side != null &&
                        ((event.isPlayerOne && side == HumanSide.PLAYER_TWO) ||
                            (!event.isPlayerOne && side == HumanSide.PLAYER_ONE))
                    if (isAiMove) delay(Dimens.ThinkDelayMs)
                    animationState.playMove(event)
                    displayedStatus = event.statusAfter
                }
            }
        }
    }

    val activeMancala = when (displayedStatus) {
        Game.GameStatus.PlayerOneTurn -> Board.PLAYER_ONE_MANCALA
        Game.GameStatus.PlayerTwoTurn -> Board.PLAYER_TWO_MANCALA
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BoardColors.TableFelt),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoardLayout(
            animationState = animationState,
            onPitClick = viewModel::onPitClick,
            isLegalMove = viewModel::isLegalMove,
            activeMancala = activeMancala,
            humanSide = humanSide,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 25.dp, vertical = 16.dp),
        )

        TurnIndicator(
            status = displayedStatus,
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
                onClick = { viewModel.restart(HumanSide.PLAYER_ONE) },
            )
            NewGameButton(
                text = "New Game — AI First",
                onClick = { viewModel.restart(HumanSide.PLAYER_TWO) },
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
    val viewModel = remember {
        MancalaBoardViewModel(InMemoryGameRepository())
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(size = DpSize(900.dp, 900.dp)),
        title = "Mancala",
    ) {
        AppScreen(viewModel)
    }
}
