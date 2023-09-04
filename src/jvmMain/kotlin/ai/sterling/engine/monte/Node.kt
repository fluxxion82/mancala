package ai.sterling.engine.monte

import ai.sterling.model.Game
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A particular node in the search tree. Used internally to track
 * the progress of the MCTS algorithm.
 */
data class Node(
    val game: Game,
    val parent: Node? = null
) {
    var visitCount: Int = 0
    var winCount: Double = 0.0
    val children: MutableMap<Int, Node> = mutableMapOf()

    // A heuristic evaluation of the board state at this node.
    var heuristicValue: Double = 0.0

    fun isLeaf() = children.isEmpty()

    fun add(move: Int, game: Game): Node {
        val newNode = Node(game, this)
        children[move] = newNode
        return newNode
    }

    fun visited(winners: List<Int>) {
        visitCount++
        if (winners.contains(game.getBoardCurrentPlayer())) {
            winCount++
        }
    }

    // The UCT formula with some custom enhancements
    private fun uctValue(explorationFactor: Double = 1.0, lambda: Double = 0.1): Double {
        if (visitCount == 0) {
            return Double.POSITIVE_INFINITY  // Unvisited node
        }

        val exploitation = winCount / visitCount
        val exploration = sqrt(ln(parent?.visitCount?.toDouble() ?: 1.0) / visitCount)

        // Adding a game-specific heuristic term to the UCT formula
        return exploitation + explorationFactor * exploration + lambda * heuristicValue
    }

    fun bestChild(explorationFactor: Double = 1.0, lambda: Double = 0.1): Node {
        return children.values.maxByOrNull { it.uctValue(explorationFactor, lambda) } ?: this  // 'this' as a fallback
    }
}