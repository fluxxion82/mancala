package ai.sterling.ui.board

import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.ui.theme.BoardColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TurnIndicator(
    status: Game.GameStatus,
    humanSide: HumanSide?,
    modifier: Modifier = Modifier,
) {
    if (humanSide == null) return
    val (label, accent) = labelFor(status, humanSide)

    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.0f),
                        accent.copy(alpha = 0.30f),
                        accent.copy(alpha = 0.0f),
                    ),
                ),
                shape = RoundedCornerShape(50),
            )
            .padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = BoardColors.Parchment,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

private val YourTurn = "Your turn" to BoardColors.GlowAmber
private val ComputerTurn = "Computer thinking…" to Color(0xFFB87333)
private val YouWin = "You win!" to BoardColors.GlowAmber
private val ComputerWins = "Computer wins" to Color(0xFFEF476F)

private fun labelFor(status: Game.GameStatus, humanSide: HumanSide?): Pair<String, Color> {
    return when (status) {
        Game.GameStatus.PlayerOneTurn ->
            if (humanSide == HumanSide.PLAYER_ONE) YourTurn else ComputerTurn
        Game.GameStatus.PlayerTwoTurn ->
            if (humanSide == HumanSide.PLAYER_TWO) YourTurn else ComputerTurn
        Game.GameStatus.Finished.PlayerOneWin ->
            if (humanSide == HumanSide.PLAYER_ONE) YouWin else ComputerWins
        Game.GameStatus.Finished.PlayerTwoWin ->
            if (humanSide == HumanSide.PLAYER_TWO) YouWin else ComputerWins
        Game.GameStatus.Finished.Draw -> "Draw" to BoardColors.ParchmentDim
    }
}
