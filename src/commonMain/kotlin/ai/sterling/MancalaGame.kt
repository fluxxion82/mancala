package ai.sterling

import ai.sterling.data.InMemoryGameRepository
import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.mancala.resources.Res
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Public root composable for the game. Embed this from anywhere on a host project.
 * Handles async model loading and shows a placeholder while weights are downloading.
 *
 * AI strength controls (in priority order):
 *
 * - [aiTimeBudgetMs] — if > 0, the engine uses iterative-deepening search with this
 *   wall-clock budget per AI move. The depth reached is whatever fits in that budget,
 *   so the AI scales up automatically on faster devices. This is the recommended mode.
 *
 * - [aiMaxDepth] — hard cap on iterative deepening (so even an idle thread doesn't
 *   spend forever searching).
 *
 * - [searchDepth] — only used when [aiTimeBudgetMs] is 0. Fixed search depth, same
 *   knob as before. Kept for backwards compatibility / fully-deterministic play.
 *
 * Defaults: 600ms budget, max depth 6. Tuned to keep the browser responsive while
 * giving the AI enough time to look 2–4 plies ahead on a typical desktop browser.
 * Crank [aiTimeBudgetMs] up on the desktop app where there's a real thread pool.
 */
@Composable
fun MancalaGame(
    modifier: Modifier = Modifier,
    searchDepth: Int = 1,
    aiTimeBudgetMs: Long = 600L,
    aiMaxDepth: Int = 6,
) {
    var engine by remember { mutableStateOf<NeuralNetEngine?>(null) }
    LaunchedEffect(searchDepth) {
        engine = MancalaWeightsCache.loadEngine(searchDepth = searchDepth)
    }
    val nn = engine
    if (nn == null) {
        Box(
            modifier = modifier.fillMaxSize().background(BoardColors.TableFelt),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading model…",
                color = BoardColors.Parchment,
                fontSize = 14.sp,
            )
        }
        return
    }
    val viewModel = remember(nn, aiTimeBudgetMs, aiMaxDepth) {
        MancalaBoardViewModel(
            InMemoryGameRepository(
                neuralNetEngine = nn,
                aiTimeBudgetMs = aiTimeBudgetMs,
                aiMaxDepth = aiMaxDepth,
            ),
        )
    }
    MancalaBoardScreen(viewModel = viewModel, modifier = modifier)
}

/**
 * Eagerly fetches and caches the model weight bytes so a later [MancalaGame] mount
 * resolves quickly. Call from a [LaunchedEffect] in a parent screen the user is
 * likely to visit before the game itself (e.g. the project listing).
 */
suspend fun preloadMancalaWeights() {
    MancalaWeightsCache.warm()
}

private object MancalaWeightsCache {
    private val mutex = Mutex()
    private var cachedBytes: ByteArray? = null

    suspend fun warm() {
        if (cachedBytes != null) return
        mutex.withLock {
            if (cachedBytes != null) return@withLock
            cachedBytes = readWeightBytes()
        }
    }

    suspend fun loadEngine(searchDepth: Int): NeuralNetEngine {
        val bytes = cachedBytes ?: mutex.withLock {
            cachedBytes ?: readWeightBytes().also { cachedBytes = it }
        }
        return NeuralNetEngine.create(searchDepth = searchDepth, weightBytes = bytes)
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun readWeightBytes(): ByteArray =
        Res.readBytes(NeuralNetEngine.WEIGHTS_RESOURCE_PATH)
}

@Composable
private fun MancalaBoardScreen(
    viewModel: MancalaBoardViewModel,
    modifier: Modifier = Modifier,
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
            try {
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
                        println("[mancala] event MoveApplied pos=${event.position} isP1=${event.isPlayerOne} isAi=$isAiMove status=${event.statusAfter}")
                        if (isAiMove) delay(Dimens.ThinkDelayMs)
                        animationState.playMove(event)
                        displayedStatus = event.statusAfter
                        println("[mancala] event MoveApplied done pos=${event.position}")
                    }
                }
            } catch (t: Throwable) {
                println("[mancala] event handler threw: ${t::class.simpleName}: ${t.message}")
                t.printStackTrace()
                throw t
            }
        }
    }

    val activeMancala = when (displayedStatus) {
        Game.GameStatus.PlayerOneTurn -> Board.PLAYER_ONE_MANCALA
        Game.GameStatus.PlayerTwoTurn -> Board.PLAYER_TWO_MANCALA
        else -> null
    }

    Column(
        modifier = modifier.fillMaxSize().background(BoardColors.TableFelt),
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
