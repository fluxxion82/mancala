package ai.sterling.engine.monte

import ai.sterling.model.Game

/**
 * A particular node in the search tree. Used internally to track
 * the progress of the MCTS algorithm.
 */
class Node {
    /**
     * The game at this node in the search tree.
     */
    val game: Game

    val children
        get() = moveToChild as Map<Int, Node>

    /**
     * Nodes reachable from this node by a single move.
     */
    private val moveToChild = HashMap<Int, Node>()

    /**
     * The node used to reach this node.
     */
    val parent: Node?

    /**
     * The number of times this node has been visited by the search
     * algorithm.
     */
    var visitCount = 0
        private set

    /**
     * The number of visits to this node by the search algorithm
     * that have resulted in simulated victories for the player
     * associated with the node.
     */
    var winCount = 0
        private set

    constructor(game: Game) {
        this.parent = null
        this.game = game
    }

    private constructor(game: Game, parent: Node) {
        this.parent = parent
        this.game = game
    }

    /**
     * Add a new child node to this one that will represent the
     * result of the given player having chosen the given move
     * at the parent node, producing the given board state.
     */
    fun add(move: Int, game: Game): Node {
        val node = Node(game, this)
        moveToChild[move] = node
        return node
    }

    /**
     * Whether or not this node is a leaf node.
     */
    fun isLeaf() = moveToChild.size == 0

    /**
     * Report a new visit to this node, mutating its visit
     * count and (possibly) win count in the process.
     */
    fun visited(winner: List<Int>) {
        visitCount++

        if (parent == null || winner.contains(parent.game.getBoardCurrentPlayer())) {
            // The root node always wins because play always
            // passes through it.
            winCount++
        }
    }
}
