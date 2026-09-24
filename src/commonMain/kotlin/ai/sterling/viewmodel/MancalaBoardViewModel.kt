package ai.sterling.viewmodel

import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.model.MoveEvent
import ai.sterling.repository.GameRepository
import ai.sterling.session.GameSession
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compose-side wrapper around the engine's UI-agnostic [GameSession]. The session
 * owns the game flow (turns, AI replies, restarts); this adds only the side-picker
 * overlay state and ties the session's lifetime to [viewModelScope].
 */
class MancalaBoardViewModel(
    repository: GameRepository,
) : ViewModel() {
    private val session = GameSession(repository, viewModelScope)

    val game: StateFlow<Game> = session.game
    val humanSide: StateFlow<HumanSide?> = session.humanSide
    val events: SharedFlow<MoveEvent> = session.events

    private val _showSidePicker = MutableStateFlow(false)
    val showSidePicker: StateFlow<Boolean> = _showSidePicker.asStateFlow()

    fun isLegalMove(position: Int): Boolean = session.isLegalMove(position)

    fun onPitClick(position: Int) {
        session.playHumanMove(position)
    }

    fun restart(side: HumanSide) {
        session.restart(side)
    }

    fun onOpenSidePicker() {
        _showSidePicker.value = true
    }

    fun onDismissSidePicker() {
        _showSidePicker.value = false
    }

    fun onSideChosen(side: HumanSide) {
        session.restart(side)
        _showSidePicker.value = false
    }
}
