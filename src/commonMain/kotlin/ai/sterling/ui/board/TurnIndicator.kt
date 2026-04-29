package ai.sterling.ui.board

import ai.sterling.model.Game
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
    modifier: Modifier = Modifier,
) {
    val (label, accent) = labelFor(status)

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

private fun labelFor(status: Game.GameStatus): Pair<String, Color> = when (status) {
    Game.GameStatus.PlayerOneTurn -> "Your turn" to BoardColors.GlowAmber
    Game.GameStatus.PlayerTwoTurn -> "Computer thinking…" to Color(0xFFB87333)
    Game.GameStatus.Finished.PlayerOneWin -> "You win!" to BoardColors.GlowAmber
    Game.GameStatus.Finished.PlayerTwoWin -> "Computer wins" to Color(0xFFEF476F)
    Game.GameStatus.Finished.Draw -> "Draw" to BoardColors.ParchmentDim
}
