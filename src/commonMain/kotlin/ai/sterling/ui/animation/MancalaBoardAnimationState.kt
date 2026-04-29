package ai.sterling.ui.animation

import ai.sterling.model.Board
import ai.sterling.ui.theme.Dimens
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * UI-layer state holder for the Mancala board's animated visuals. Owns the visual
 * stones, pit positions, and in-flight animation state. Has no knowledge of game
 * truth — the Composable observes the ViewModel's MoveEvent stream and drives
 * [playMove] / [handleReset] off it.
 */
@Stable
class MancalaBoardAnimationState {

    val visualStones = mutableStateListOf<VisualStone>()
    val pitCenters = mutableStateMapOf<Int, Offset>()
    val pitBounds = mutableStateMapOf<Int, Rect>()
    val inFlight = mutableStateMapOf<Long, FlightState>()
    val hoveredPit = mutableStateOf<Int?>(null)

    /** True while any stone is in flight. The Composable uses this to gate input. */
    val isAnimating: Boolean
        get() = inFlight.isNotEmpty()

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

    /** Count of stones at rest in [pit] (excludes any currently in flight). */
    fun visibleCount(pit: Int): Int =
        visualStones.count { it.pit == pit && it.id !in inFlight.keys }

    fun handleReset() {
        inFlight.clear()
        seedVisualStones()
    }

    /** Animates the sowing for a move. Suspends until all stones land and the board is reconciled. */
    suspend fun playMove(event: MoveEvent.MoveApplied) {
        val plan = SowingPlanner.plan(
            boardPocketsBefore = event.boardBeforePockets,
            position = event.position,
            isPlayerOne = event.isPlayerOne,
        )

        // Sow → capture → sweep run as sequential phases so a stone that was just sown
        // into a pit cannot be re-animated out of that pit before its sow flight finishes
        // (which would make the in-flight FlightState get overwritten and the stone
        // appear to teleport mid-flight on capture moves).
        val sowMoves = plan.moves.filter { it.kind == StoneMoveKind.SOW }
        val captureMoves = plan.moves.filter {
            it.kind == StoneMoveKind.CAPTURE_OPPOSITE || it.kind == StoneMoveKind.CAPTURE_LANDING
        }
        val sweepMoves = plan.moves.filter { it.kind == StoneMoveKind.SWEEP }

        runPhase(sowMoves)
        runPhase(captureMoves)
        runPhase(sweepMoves)

        // Reconcile against the authoritative board. Stable on planner correctness; safety net otherwise.
        snapStonesToBoard(event.boardAfter.pockets)
    }

    private suspend fun runPhase(moves: List<StoneMove>) {
        if (moves.isEmpty()) return

        // Rebuild the stone-id deque from the current resting positions. Each phase awaits
        // the previous one, so visualStones is up-to-date and the originals + any sown
        // arrivals are correctly accounted for.
        val byPit: MutableMap<Int, ArrayDeque<Long>> =
            visualStones.groupByTo(mutableMapOf()) { it.pit }
                .mapValues { (_, list) -> ArrayDeque(list.map { it.id }) }
                .toMutableMap()

        coroutineScope {
            moves.forEachIndexed { idx, move ->
                val stoneId = byPit[move.fromPit]?.removeFirstOrNull()
                if (stoneId == null) {
                    // Defensive: planner should match visual stone counts; if drift, skip.
                    return@forEachIndexed
                }
                byPit.getOrPut(move.toPit) { ArrayDeque() }.addLast(stoneId)
                launch {
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
            }
        }
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

    private fun stone(id: Long, pit: Int): VisualStone {
        val rng = Random(id)
        val theta = rng.nextFloat() * (kotlin.math.PI * 2).toFloat()
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
