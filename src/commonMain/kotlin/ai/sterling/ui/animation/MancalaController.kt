package ai.sterling.ui.animation

import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.model.isHumansTurn
import ai.sterling.ui.theme.Dimens
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * UI-layer orchestrator. Owns visual stones, pit positions, input gating, and the in-flight
 * animation state. Drives the AI loop on the UI side so animations can play between turns.
 *
 * The ViewModel knows nothing about any of this — it just exposes game truth and emits
 * MoveEvents that the controller consumes in [runEventLoop].
 */
class MancalaController(
    private val viewModel: MancalaControllerHost,
) {

    val visualStones = mutableStateListOf<VisualStone>()
    val pitCenters = mutableStateMapOf<Int, Offset>()
    val pitBounds = mutableStateMapOf<Int, Rect>()
    val inputLocked = mutableStateOf(false)
    val inFlight = mutableStateMapOf<Long, FlightState>()
    val hoveredPit = mutableStateOf<Int?>(null)

    private val activeAnimationJobs = mutableSetOf<Job>()
    private var aiTriggerJob: Job? = null
    private var eventLoopScope: CoroutineScope? = null

    init {
        seedVisualStones()
    }

    fun seedVisualStones() {
        visualStones.clear()
        var nextId = 0L
        for (pit in 0..5) {
            repeat(4) { visualStones.add(stone(nextId++, pit)) }
        }
        for (pit in 7..12) {
            repeat(4) { visualStones.add(stone(nextId++, pit)) }
        }
    }

    fun onPitClick(index: Int) {
        if (inputLocked.value) return
        val status = viewModel.currentGameStatus()
        val human = viewModel.currentHumanSide() ?: return  // no game in progress yet
        if (!status.isHumansTurn(human)) return
        val onHumansSide = when (human) {
            HumanSide.PLAYER_ONE -> index in 0..5
            HumanSide.PLAYER_TWO -> index in 7..12
        }
        if (!onHumansSide) return
        if (viewModel.currentPockets()[index] == 0) return
        viewModel.applyMove(index)
    }

    /**
     * Count of stones visually at rest in a pit (i.e. not currently in flight). Updates live
     * as stones leave and arrive, giving the UI a count that ticks down/up smoothly.
     */
    fun visibleCount(pit: Int): Int =
        visualStones.count { it.pit == pit && it.id !in inFlight.keys }

    fun isClickablePit(index: Int): Boolean {
        if (inputLocked.value) return false
        if (index == Board.PLAYER_ONE_MANCALA || index == Board.PLAYER_TWO_MANCALA) return false
        val status = viewModel.currentGameStatus()
        val human = viewModel.currentHumanSide() ?: return false  // no game in progress yet
        if (!status.isHumansTurn(human)) return false
        val onHumansSide = when (human) {
            HumanSide.PLAYER_ONE -> index in 0..5
            HumanSide.PLAYER_TWO -> index in 7..12
        }
        if (!onHumansSide) return false
        return viewModel.currentPockets()[index] > 0
    }

    suspend fun runEventLoop(scope: CoroutineScope) {
        eventLoopScope = scope
        // Wait for first layout pass so all 14 pit centers are populated.
        snapshotFlow { pitCenters.size == Board.TOTAL_POCKETS }.first { it }

        viewModel.events().collect { event ->
            when (event) {
                MoveEvent.Reset -> handleReset()
                is MoveEvent.MoveApplied -> animateMove(scope, event)
            }
        }
    }

    private fun handleReset() {
        stopAnimations()
        seedVisualStones()
        inputLocked.value = false

        // If a game is in progress and starts on the AI's turn (human picked
        // Player 2), kick off the first AI move now — Reset emits no MoveApplied
        // event so the trigger in animateMove() never fires for the opening move.
        // If no side has been chosen yet (humanSide == null), do nothing.
        val human = viewModel.currentHumanSide() ?: return
        val status = viewModel.currentGameStatus()
        if (status !is Game.GameStatus.Finished && !status.isHumansTurn(human)) {
            triggerAiMove()
        }
    }

    private fun triggerAiMove() {
        val scope = eventLoopScope ?: return
        aiTriggerJob?.cancel()
        aiTriggerJob = scope.launch {
            delay(Dimens.ThinkDelayMs)
            // Re-check after the delay — a Reset (or game completion) may have
            // intervened, in which case we must not apply a stale move.
            val status = viewModel.currentGameStatus()
            val human = viewModel.currentHumanSide() ?: return@launch
            if (status is Game.GameStatus.Finished) return@launch
            if (status.isHumansTurn(human)) return@launch
            val ai = viewModel.computeAiMove()
            // computeAiMove may suspend; re-verify after it returns too.
            val statusAfter = viewModel.currentGameStatus()
            val humanAfter = viewModel.currentHumanSide() ?: return@launch
            if (statusAfter is Game.GameStatus.Finished) return@launch
            if (statusAfter.isHumansTurn(humanAfter)) return@launch
            viewModel.applyMove(ai)
        }
    }

    private suspend fun animateMove(scope: CoroutineScope, event: MoveEvent.MoveApplied) {
        inputLocked.value = true
        val plan = SowingPlanner.plan(
            boardPocketsBefore = event.boardBeforePockets,
            position = event.position,
            isPlayerOne = event.isPlayerOne,
        )

        val byPit: MutableMap<Int, ArrayDeque<Long>> =
            visualStones.groupByTo(mutableMapOf()) { it.pit }
                .mapValues { (_, list) -> ArrayDeque(list.map { it.id }) }
                .toMutableMap()

        coroutineScope {
            plan.moves.forEachIndexed { idx, move ->
                val stoneId = byPit[move.fromPit]?.removeFirstOrNull()
                if (stoneId == null) {
                    // Defensive: planner should match visual stone counts; if drift, skip.
                    return@forEachIndexed
                }
                byPit.getOrPut(move.toPit) { ArrayDeque() }.addLast(stoneId)
                val job = launch {
                    delay(idx * Dimens.StaggerMs)
                    animateOneStone(stoneId, move)
                    // Update the resting pit BEFORE clearing in-flight, otherwise the cluster
                    // briefly re-renders the stone in its old position.
                    val visIdx = visualStones.indexOfFirst { it.id == stoneId }
                    if (visIdx >= 0) {
                        visualStones[visIdx] = visualStones[visIdx].copy(pit = move.toPit)
                    }
                    inFlight.remove(stoneId)
                }
                activeAnimationJobs.add(job)
                job.invokeOnCompletion { activeAnimationJobs.remove(job) }
            }
        }

        // Reconcile against the authoritative board. Stable on planner correctness; safety net otherwise.
        snapStonesToBoard(event.boardAfter.pockets)
        inputLocked.value = false

        if (event.statusAfter !is Game.GameStatus.Finished &&
            !event.statusAfter.isHumansTurn(viewModel.currentHumanSide())) {
            triggerAiMove()
        }
    }

    private suspend fun animateOneStone(stoneId: Long, move: StoneMove) {
        val from = pitCenters[move.fromPit] ?: return
        val to = pitCenters[move.toPit] ?: return

        val tAnim = Animatable(0f)
        val scaleAnim = Animatable(1f)
        val colorSeed = visualStones.firstOrNull { it.id == stoneId }?.colorSeed ?: stoneId.toInt()

        inFlight[stoneId] = FlightState(
            tAnim = tAnim,
            scaleAnim = scaleAnim,
            from = from,
            to = to,
            kind = move.kind,
            colorSeed = colorSeed,
        )

        if (move.kind == StoneMoveKind.CAPTURE_OPPOSITE || move.kind == StoneMoveKind.CAPTURE_LANDING) {
            scaleAnim.animateTo(1.2f, tween(Dimens.CapturePulseMs))
            scaleAnim.animateTo(1f, tween(Dimens.CapturePulseMs))
        }

        tAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(Dimens.FlightMs, easing = FastOutSlowInEasing),
        )
        // Caller is responsible for clearing inFlight after updating the resting pit.
    }

    fun snapStonesToBoard(targetPockets: List<Int>) {
        val byPit: MutableMap<Int, MutableList<VisualStone>> =
            visualStones.groupByTo(mutableMapOf()) { it.pit }

        val pool = mutableListOf<VisualStone>()
        for (pit in 0 until Board.TOTAL_POCKETS) {
            val current = byPit[pit] ?: continue
            val target = targetPockets[pit]
            while (current.size > target) {
                pool.add(current.removeAt(current.lastIndex))
            }
        }
        for (pit in 0 until Board.TOTAL_POCKETS) {
            val current = byPit.getOrPut(pit) { mutableListOf() }
            val target = targetPockets[pit]
            while (current.size < target && pool.isNotEmpty()) {
                val s = pool.removeAt(pool.lastIndex)
                current.add(s.copy(pit = pit))
            }
        }

        val rebuilt = byPit.values.flatten().sortedBy { it.id }
        visualStones.clear()
        visualStones.addAll(rebuilt)
    }

    private fun stopAnimations() {
        activeAnimationJobs.toList().forEach { it.cancel() }
        activeAnimationJobs.clear()
        aiTriggerJob?.cancel()
        aiTriggerJob = null
        inFlight.clear()
    }

    private fun stone(id: Long, pit: Int): VisualStone {
        val rng = Random(id)
        val theta = rng.nextFloat() * (Math.PI * 2).toFloat()
        val r = rng.nextFloat()
        return VisualStone(
            id = id,
            pit = pit,
            colorSeed = id.toInt(),
            jitter = Offset(
                x = kotlin.math.cos(theta) * r,
                y = kotlin.math.sin(theta) * r,
            ),
        )
    }
}

data class FlightState(
    val tAnim: Animatable<Float, *>,
    val scaleAnim: Animatable<Float, *>,
    val from: Offset,
    val to: Offset,
    val kind: StoneMoveKind,
    val colorSeed: Int,
)

/**
 * Minimal contract the controller needs from the ViewModel. Lets the UI controller live in
 * commonMain even though the concrete ViewModel lives in jvmMain.
 */
interface MancalaControllerHost {
    fun events(): kotlinx.coroutines.flow.SharedFlow<MoveEvent>
    fun currentGameStatus(): Game.GameStatus
    fun currentPockets(): List<Int>
    /** Null means no game is in progress — the user has not picked a side yet. */
    fun currentHumanSide(): HumanSide?
    fun applyMove(position: Int)
    suspend fun computeAiMove(): Int
}
