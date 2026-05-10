package ai.sterling.ui.board

import ai.sterling.model.HumanSide
import ai.sterling.ui.theme.BoardColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SidePickerDialog(
    dismissible: Boolean,
    onSideChosen: (HumanSide) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(BoardColors.WoodMid)
                .border(1.dp, BoardColors.WoodGrain, RoundedCornerShape(16.dp))
                .padding(PaddingValues(horizontal = 24.dp, vertical = 22.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Choose your side to start",
                color = BoardColors.Parchment,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            SidePickerButton(
                text = "Play as First Player",
                onClick = { onSideChosen(HumanSide.PLAYER_ONE) },
            )
            SidePickerButton(
                text = "Play as Second Player",
                onClick = { onSideChosen(HumanSide.PLAYER_TWO) },
            )
        }
    }
}

@Composable
private fun SidePickerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = BoardColors.WoodLight,
            contentColor = BoardColors.Parchment,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
