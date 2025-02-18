package ai.sterling.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MancalaBoard(
    modifier: Modifier,
    stones: List<Int> = Array(14) { 0 }.toList(),
    onPocketClicked: (Int) -> Unit
) {
    Row(
        modifier = modifier.background(color = Color.White)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterVertically).weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            // left Mancala
            Pocket(
                Modifier,
                alignment = Alignment.CenterStart,
                stoneValue = stones[13],
            )
        }

        Column(
            modifier = Modifier.weight(6f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // top row
            RowOfPockets(
                modifier = Modifier.padding(bottom = 10.dp),
                numOfPockets = 6,
                alignment = Alignment.TopCenter,
                stones = stones.subList(7, 13).reversed(),
            ) {
                val topPocketValue = 12 - it
                onPocketClicked(topPocketValue)
            }

            // bottom row
            RowOfPockets(
                modifier = Modifier.padding(bottom = 10.dp),
                numOfPockets = 6,
                alignment = Alignment.BottomCenter,
                stones = stones.subList(0, 6),
                onClick = onPocketClicked,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.CenterVertically).weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            // right mancala
            Pocket(
                Modifier,
                alignment = Alignment.CenterEnd,
                stoneValue = stones[6],
            )
        }
    }
}

@Composable
fun Pocket(
    modifier: Modifier = Modifier,
    alignment: Alignment,
    stoneValue: Int,
) {
    Box(
        modifier = modifier
    ) {
        Image(
            modifier = Modifier.align(Alignment.Center).padding(20.dp).alpha(if (stoneValue == 0) 0f else 1f),
            painter = painterResource("small_marbles.png"),
            contentDescription = null,
        )

        Text(
            text = stoneValue.toString(),
            modifier = Modifier.align(alignment).padding(5.dp),
            color = Color.Black,
            fontSize = 13.sp
        )
    }

    LazyColumn {
        items(listOf<String>()) {

        }
    }
}

@Composable
fun RowOfPockets(
    modifier: Modifier,
    numOfPockets: Int,
    alignment: Alignment,
    stones: List<Int>,
    onClick: (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier,
    ) {
        for (i in 0 until numOfPockets) {
            Pocket(
                modifier = Modifier
                    .padding(5.dp)
                    .clickable {
                        coroutineScope.launch {
                            onClick(i)
                            delay(500L) // debounce time
                        } },
                alignment = alignment,
                stoneValue = stones[i]
            )
        }
    }
}
