package ai.sterling.engine.monte

import ai.sterling.model.Board
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A particular node in the search tree. Used internally to track
 * the progress of the MCTS algorithm.
 */
data class Node(
    val game: Game,
    val parent: Node? = null,
    private val explorationFactor: Double = 2.0,
    private val lambda: Double = 0.8
) {
    var visitCount: Int = 0
        private set
    var winCount: Double = 0.0
        private set
    val children: MutableMap<Int, Node> = mutableMapOf()
    var heuristicValue: Double = calculateHeuristicValue()
        private set

    val isLeaf: Boolean get() = children.isEmpty()

    fun addChild(move: Int, game: Game): Node {
        return Node(game, this, explorationFactor, lambda).also {
            children[move] = it
        }
    }

    fun recordVisit(winners: List<Int>) {
        visitCount++
        if (winners.contains(getCurrentPlayer())) {
            winCount++
        }
    }

    fun getBestChild(): Node {
        return children.values.maxByOrNull { it.calculateUCT() } ?: this
    }

    private fun calculateUCT(): Double {
        if (visitCount == 0) return Double.POSITIVE_INFINITY

        val exploitation = winCount / visitCount
        val exploration = sqrt(ln(parent?.visitCount?.toDouble() ?: 1.0) / visitCount)

        return exploitation + explorationFactor * exploration + lambda * heuristicValue
    }

    private fun calculateHeuristicValue(): Double {
        val currentPlayer = getCurrentPlayer()
        val isPlayerOne = currentPlayer == 0

        val playerPockets = if (isPlayerOne) Board.PLAYER_ONE_POCKETS else Board.PLAYER_TWO_POCKETS
        val playerMancala = if (isPlayerOne) Board.PLAYER_ONE_MANCALA else Board.PLAYER_TWO_MANCALA

        for (pos in playerPockets) {
            val stones = game.board.pockets[pos]
            if (stones > 0) {
                val landingPos = (pos + stones) % Board.TOTAL_POCKETS
                if (landingPos == playerMancala) {
                    return 1.0
                }
            }
        }

        val opponentMancala = if (isPlayerOne) game.board.playerTwo.mancala else game.board.playerOne.mancala
        return (playerMancala - opponentMancala) / Board.TOTAL_STONES.toDouble()
    }

    private fun getCurrentPlayer(): Int =
        when (game.status) {
            is GameStatus.PlayerOneTurn -> 0
            is GameStatus.PlayerTwoTurn -> 1
            is GameStatus.Finished -> parent?.getCurrentPlayer() ?: 0
        }
}