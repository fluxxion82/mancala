package ai.sterling.engine.monte

import ai.sterling.model.Board
import ai.sterling.model.Game
import java.util.*

class MonteCarlo(val game: Game) {
    var currentNode = Node(game)
        private set

    /**
     * Apply a move that was computed externally (possibly by
     * a human player or another AI) to the board state held
     * by the engine.
     *
     * TODO: Add option to re-use sub-tree for better performance
     */
    fun apply(game: Game) {
        // game.makeMove(move)
        currentNode = Node(game)
    }

    fun run(iterations: Int = 1): Map<Int, Double> {
        var iteration = 0
        return runWhile { iteration++ < iterations }
    }

    fun runBest(iterations: Int): Int {
        return run(iterations).filterNot { it.value.isNaN() }.maxBy { it.value }.key
    }

    fun runBestWhile(callback: () -> Boolean): Int {
        return runWhile(callback).filterNot { it.value.isNaN() }.maxBy { it.value }.key
    }

    /**
     * Run the MCTS algorithm for as long as the provided callback
     * returns true.
     */
    fun runWhile(callback: () -> Boolean): Map<Int, Double> {
        while (callback()) {
            val leaf = expand(select(currentNode))
            val winners = simulate(leaf)
            backpropagate(leaf, winners)
        }

        return currentNode.children.map {
            val node = it.value
            val score = node.winCount * 1.0 / node.visitCount
            Pair(it.key, score)
        }.toMap()
    }

    /**
     * The back propagation phase of the algorithm. This is where
     * we record the results of our simulation in nodes of the
     * search tree that we passed through during the selection
     * and expansion phases.
     */
    fun backpropagate(leaf: Node, winner: List<Int>) {
        var current: Node? = leaf
        while (current != null) {
            current.visited(winner)
            current = current.parent
        }
    }

    /**
     * The expansion phase of the algorithm. This is where the
     * actual search tree is expanded downward and we pick the
     * node that will be used as the actual starting point for
     * the simulation phase.
     *
     * If the given leaf node has no possible moves (meaning
     * it is an end-game node) then it will be returned.
     */
    fun expand(leaf: Node): Node {
        val possibleMoves = leaf.game.board.legalMoves(game.getBoardCurrentPlayer() == 0)
        if (possibleMoves.isEmpty()) {
            return leaf
        }

        // We fully hydrate the leaf node even though we will only
        // choose one of the new leaves. This simplifies the select
        // part of the algorithm at the cost of some memory.
        val possibleNodes = possibleMoves.map { move ->
            val deep = game.deepCopy()
            deep.makeMove(move)
            leaf.add(move, deep)
        }

        return expandChoose(possibleNodes)
    }

    /**
     * Method used to choose a node during the expansion phase.
     */
    fun expandChoose(nodes: List<Node>): Node {
        println("expandChoose nodes isEmpty: ${nodes.isEmpty()}, size minus 1: ${nodes.size -1}")
        val random = if (nodes.size == 1) {
            0
        } else {
            Random().nextInt(nodes.size - 1) + 1
        }


//        nodes.take(2).filter {
//            val board = it.game.board
//            val curPlayer = it.game.getBoardCurrentPlayer()
//
//            val emptyPockets = board.pockets.mapIndexed { index, pocket ->
//                if (curPlayer == 0) {
//                    if (pocket == 0 && index in 0..5) index else -1
//                } else {
//                    if (pocket == 0 && index in 7..12) index else -1
//                }
//            }.filter { it != -1 }
//
//            val opponentMoves = board.legalMoves(curPlayer == 0)
//
//            val scorePocket = if (curPlayer == 0) 6 else 13
//            val canScore = it.children.keys.any { key -> key + board.pockets[key] == scorePocket }
//
//            val canCapture = opponentMoves.any { move ->
//                emptyPockets.contains(12 - board.pockets[move])
//            }
//
//            canScore || canCapture
//        }.ifEmpty {
//            nodes
//        }

        return nodes[random]
    }

    /**
     * The selection phase of the algorithm. This is where we
     * decide where to expand the tree so that we can run a
     * simulation.
     */
    fun select(root: Node): Node {
        var current = root
        while (!current.isLeaf()) {
            current = selectChoose(current.children.values.toList())
        }
        return current
    }

    /**
     * Method used to choose a node at each step of the selection
     * phase. Probably some kind of UCT.
     */
    fun selectChoose(nodes: List<Node>): Node {
        // TODO: UCT algorithm goes here
        val random = if (nodes.size == 1) {
            0
        } else {
            Random().nextInt(nodes.size - 1) + 1
        }

        nodes.take(2).filter {
            val board = it.game.board
            val curPlayer = it.game.getBoardCurrentPlayer()

            val emptyPockets = board.pockets.mapIndexed { index, pocket ->
                if (curPlayer == 0) {
                    if (pocket == 0 && index in 0..5) index else -1
                } else {
                    if (pocket == 0 && index in 7..12) index else -1
                }
            }.filter { it != -1 }

            val opponentMoves = board.legalMoves(curPlayer == 0)

            val scorePocket = if (curPlayer == 0) 6 else 13
            val canScore = it.children.keys.any { key -> key + board.pockets[key] == scorePocket }

            val canCapture = opponentMoves.any { move ->
                emptyPockets.contains(12 - board.pockets[move])
            }

            canScore || canCapture
        }.ifEmpty {
            nodes
        }

        return nodes[random]
    }

    /**
     * The simulation phase of the algorithm. During this step we
     * play the game to the end and see who won, this step does
     * not expand or otherwise mutate the search tree itself.
     *
     * The return value is a list of identifiers of the players
     * that "won" (because they had the highest score). Usually
     * there will only be one identifier in the list, but there
     * can be more than one in the case of a draw.
     *
     * The list returned will never be empty.
     *
     * TODO: This would be more efficient if the board was optionally mutable
     */
    fun simulate(leaf: Node): List<Int> {
        val currentGame = leaf.game.deepCopy()
        var status = leaf.game.status
        while (!currentGame.isGameOver()) {
            val move = simulateChoose(
                currentGame.board.legalMoves(currentGame.getBoardCurrentPlayer() == 0),
                currentGame.board
            )
            status = currentGame.makeMove(move)
            // currentGame = currentGame.deepCopy()
        }

        // We now have a board state for which `isOver` returns
        // `true`, therefore we know that there will be at least
        // one key-value pair in the map, we just need to find
        // the maximum score and return a list of the keys that
        // have that score.

        return when (status) {
            Game.Status.Finished.Draw -> listOf(0, 1)
            Game.Status.Finished.PlayerOneWin -> listOf(0)
            Game.Status.Finished.PlayerTwoWin -> listOf(1)
            Game.Status.PlayerOneTurn,
            Game.Status.PlayerTwoTurn -> error("game is over so no more turns")
        }
    }

    /**
     * Method used to choose a move during each step of the simulation
     * phase. Note that this phase does not expand the search tree.
     */
    fun simulateChoose(moves: List<Int>, board: Board): Int {
        val random = if (moves.size == 1) {
            0
        } else {
            Random().nextInt(moves.size - 1) + 1
        }

        val curPlayer = if (board.playerOne.turn) 0 else 1

        val emptyPockets = board.pockets.mapIndexed { index, pocket ->
            if (curPlayer == 0) {
                if (pocket == 0 && index in 0..5) index else -1
            } else {
                if (pocket == 0 && index in 7..12) index else -1
            }
        }.filter { it != -1 }

        val scorePocket = if (curPlayer == 0) 6 else 13
        val canScore = moves.any { it + board.pockets[it] == scorePocket }

        val canCapture = moves.any { move ->
            emptyPockets.contains(12 - board.pockets[move])
        }

        return if (canScore || canCapture) {
            val scoring = moves.filter { it + board.pockets[it] == scorePocket }
            if (scoring.isEmpty()) {
                moves.first { move ->
                    emptyPockets.contains(12 - board.pockets[move])
                }
            } else {
                scoring.first()
            }
        } else {
            if (board.playerOne.turn) {
                moves.minOf { it }
            } else {
                moves.sorted().reversed().first()
            }
            // moves[random]
        }
    }
}
