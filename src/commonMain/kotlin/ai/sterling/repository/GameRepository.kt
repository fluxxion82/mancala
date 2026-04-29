package ai.sterling.repository

import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.ui.animation.MoveEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface GameRepository {
    val game: StateFlow<Game>
    val humanSide: StateFlow<HumanSide?>
    val events: SharedFlow<MoveEvent>

    fun applyMove(position: Int)
    suspend fun computeAiMove(): Int
    fun restart(humanSide: HumanSide)
}
