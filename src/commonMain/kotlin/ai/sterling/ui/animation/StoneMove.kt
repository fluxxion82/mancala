package ai.sterling.ui.animation

enum class StoneMoveKind {
    SOW,
    CAPTURE_OPPOSITE,
    CAPTURE_LANDING,
    SWEEP,
}

data class StoneMove(
    val fromPit: Int,
    val toPit: Int,
    val kind: StoneMoveKind,
)

data class AnimationPlan(
    val moves: List<StoneMove>,
    val finalPockets: List<Int>,
)
