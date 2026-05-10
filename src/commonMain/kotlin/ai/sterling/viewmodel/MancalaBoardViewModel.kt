package ai.sterling.viewmodel

import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.HumanSide
import ai.sterling.model.isHumansTurn
import ai.sterling.repository.GameRepository
import ai.sterling.ui.animation.MoveEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MancalaBoardViewModel(
    private val repository: GameRepository,
) : ViewModel() {
    val game: StateFlow<Game> = repository.game
    val humanSide: StateFlow<HumanSide?> = repository.humanSide
    val events: SharedFlow<MoveEvent> = repository.events

    private val _showSidePicker = MutableStateFlow(true)
    val showSidePicker: StateFlow<Boolean> = _showSidePicker.asStateFlow()

    init {
        // Auto-pump AI moves whenever (game, humanSide) settles on a state where
        // it is the AI's turn. The repository is purely data; the VM is responsible
        // for advancing the game when the human is not the next mover.
        viewModelScope.launch {
            combine(game, humanSide) { g, h -> g to h }
                .distinctUntilChanged()
                .collectLatest { (g, side) ->
                    // collectLatest cancels this block if a new state arrives while
                    // computeAiMove is still suspended (e.g. user restarts mid-think).
                    side ?: return@collectLatest
                    val status = g.status
                    if (status is Game.GameStatus.Finished) return@collectLatest
                    if (status.isHumansTurn(side)) return@collectLatest

                    val ai = repository.computeAiMove()
                    repository.applyMove(ai)
                }
        }
    }

    fun isLegalMove(position: Int): Boolean {
        if (position == Board.PLAYER_ONE_MANCALA || position == Board.PLAYER_TWO_MANCALA) return false
        val side = humanSide.value ?: return false
        val status = game.value.status
        if (!status.isHumansTurn(side)) return false
        val onHumansSide = when (side) {
            HumanSide.PLAYER_ONE -> position in 0..5
            HumanSide.PLAYER_TWO -> position in 7..12
        }
        if (!onHumansSide) return false
        return game.value.board.pockets[position] > 0
    }

    fun onPitClick(position: Int) {
        if (!isLegalMove(position)) return
        repository.applyMove(position)
    }

    fun restart(side: HumanSide) {
        repository.restart(side)
    }

    fun onOpenSidePicker() {
        _showSidePicker.value = true
    }

    fun onDismissSidePicker() {
        _showSidePicker.value = false
    }

    fun onSideChosen(side: HumanSide) {
        repository.restart(side)
        _showSidePicker.value = false
    }
}
