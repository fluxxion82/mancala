package ai.sterling

import ai.sterling.data.InMemoryGameRepository
import ai.sterling.engine.AiBackend
import ai.sterling.loading.MANCALA_WEIGHTS_VERSION
import ai.sterling.loading.MancalaLoadingScreen
import ai.sterling.loading.WeightLoadingState
import ai.sterling.loading.fetchWeightBytes
import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.ui.animation.MancalaBoardAnimationState
import ai.sterling.ui.animation.MoveEvent
import ai.sterling.ui.board.BoardLayout
import ai.sterling.ui.board.SidePickerOverlay
import ai.sterling.ui.board.TurnIndicator
import ai.sterling.ui.theme.BoardColors
import ai.sterling.ui.theme.Dimens
import ai.sterling.util.MancalaDebug
import ai.sterling.viewmodel.MancalaBoardViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Public root composable for the game. Embed this from anywhere on a host project.
 * Handles async model loading and shows a placeholder while weights are downloading.
 *
 * [aiMode] selects between alpha-beta search ([AiMode.AlphaBeta]) — the original
 * iterative-deepening engine — and AlphaZero-style PUCT MCTS ([AiMode.Mcts]) which
 * uses the policy + value heads inside a tree search. Defaults to alpha-beta with
 * a 600ms budget so behavior is unchanged unless callers opt in.
 *
 * Crank up [AiMode.Mcts.timeBudgetMs] (or [AiMode.Mcts.simulations]) on desktop
 * where there's a real thread pool — MCTS scales nicely with budget.
 */
@Composable
fun MancalaGame(
    modifier: Modifier = Modifier,
    aiMode: AiMode = AiMode.AlphaBeta(),
) {
    var backend by remember { mutableStateOf<AiBackend?>(null) }
    val loadingState by MancalaWeightsCache.state.collectAsState()
    var retryNonce by remember { mutableStateOf(0) }
    LaunchedEffect(retryNonce) {
        backend = try {
            MancalaWeightsCache.loadBackend()
        } catch (t: Throwable) {
            null
        }
    }
    val ai = backend
    if (ai == null) {
        MancalaLoadingScreen(
            state = loadingState,
            onRetry = { retryNonce++ },
            modifier = modifier,
        )
        return
    }
    val viewModel = remember(ai, aiMode) {
        MancalaBoardViewModel(
            InMemoryGameRepository(
                aiBackend = ai,
                aiMode = aiMode,
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

    private val _state = MutableStateFlow<WeightLoadingState>(WeightLoadingState.Idle)
    val state: StateFlow<WeightLoadingState> = _state.asStateFlow()

    suspend fun warm() {
        if (cachedBytes != null) return
        mutex.withLock {
            if (cachedBytes != null) return@withLock
            cachedBytes = loadBytesWithState()
        }
    }

    suspend fun loadBackend(): AiBackend {
        val bytes = cachedBytes ?: mutex.withLock {
            cachedBytes ?: loadBytesWithState().also { cachedBytes = it }
        }
        // The platform's weight fetcher emits Initializing; that's the right state
        // to keep showing while the engine spins up (worker construction + parse).
        _state.value = WeightLoadingState.Initializing
        try {
            val backend = createAiBackend(bytes)
            _state.value = WeightLoadingState.Ready
            return backend
        } catch (t: Throwable) {
            _state.value = WeightLoadingState.Error(t)
            throw t
        }
    }

    private suspend fun loadBytesWithState(): ByteArray =
        try {
            fetchWeightBytes(MANCALA_WEIGHTS_VERSION) { phase ->
                _state.value = phase
            }
        } catch (t: Throwable) {
            _state.value = WeightLoadingState.Error(t)
            throw t
        }
}

/**
 * Platform-specific factory: on JVM/desktop runs the engine in-process; on Wasm/web
 * proxies to a Web Worker so inference doesn't block the UI thread.
 */
internal expect suspend fun createAiBackend(weightBytes: ByteArray): AiBackend

/**
 * CoroutineContext the repository uses to run AI compute. JVM = a single-lane
 * `Dispatchers.Default` (the engine's mutable search tree + TT make concurrent calls
 * unsafe, so we pin one worker thread) so the search doesn't block the UI thread.
 * Wasm = `EmptyCoroutineContext` because the platform has no background threads;
 * the worker actual handles off-main work via `MancalaBackendFactory.override`.
 */
internal expect val aiDispatcher: kotlin.coroutines.CoroutineContext

/**
 * Override hook for the wasm backend factory. The host site sets this at startup
 * (e.g. to plug in a Web Worker host that knows its own bundle URL), and the
 * platform-specific [createAiBackend] consults it before falling back to in-process.
 *
 * Setting this is a no-op on JVM. Kept in commonMain so the call site doesn't need
 * platform-specific initialization code.
 */
public object MancalaBackendFactory {
    /** Set by the host before the first [MancalaGame] mounts. */
    public var override: (suspend (ByteArray) -> AiBackend)? = null
}

@Composable
private fun MancalaBoardScreen(
    viewModel: MancalaBoardViewModel,
    modifier: Modifier = Modifier,
) {
    val animationState = remember { MancalaBoardAnimationState() }
    val humanSide by viewModel.humanSide.collectAsState()
    val showSidePicker by viewModel.showSidePicker.collectAsState()

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
                        MancalaDebug.log {
                            "[mancala] event MoveApplied pos=${event.position} isP1=${event.isPlayerOne} isAi=$isAiMove status=${event.statusAfter}"
                        }
                        if (isAiMove) delay(Dimens.ThinkDelayMs)
                        animationState.playMove(event)
                        displayedStatus = event.statusAfter
                        MancalaDebug.log { "[mancala] event MoveApplied done pos=${event.position}" }
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

    Box(modifier = modifier.fillMaxSize().background(BoardColors.TableFelt)) {
        Column(
            modifier = Modifier.fillMaxSize(),
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

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    humanSide != null && displayedStatus is Game.GameStatus.Finished ->
                        NewGameButton(
                            text = "New Game",
                            onClick = viewModel::onOpenSidePicker,
                        )
                    humanSide != null ->
                        NewGameButton(
                            text = "Resign",
                            onClick = viewModel::onOpenSidePicker,
                        )
                    else ->
                        NewGameButton(
                            text = "Start",
                            onClick = viewModel::onOpenSidePicker,
                        )
                }
            }
        }

        if (showSidePicker) {
            SidePickerOverlay(
                dismissible = humanSide != null,
                onSideChosen = viewModel::onSideChosen,
                onDismiss = viewModel::onDismissSidePicker,
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
