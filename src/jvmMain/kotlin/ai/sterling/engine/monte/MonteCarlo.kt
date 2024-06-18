package ai.sterling.engine.monte

import ai.sterling.model.Game
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MonteCarlo(private val game: Game) {
    private var currentNode = Node(game)

    fun apply(game: Game) {
        currentNode = Node(game)
    }

    private fun run(iterations: Int = 1): Map<Int, Double> {
        var iteration = 0
        return runWhile { iteration++ < iterations }
    }

    fun runBest(iterations: Int): Int {
        return run(iterations).maxByOrNull { it.value }?.key ?: -1
    }

    fun runBestWhile(callback: () -> Boolean): Int {
        return runWhile(callback).maxByOrNull { it.value }?.key ?: -1
    }

    private fun runWhile(callback: () -> Boolean): Map<Int, Double> {
        while (callback()) {
            val leaf = expand(select(currentNode))
            val winners = simulate(leaf)
            backpropagate(leaf, winners)
        }

        return currentNode.children.map {
            val node = it.value
            val score = node.winCount / node.visitCount
            Pair(it.key, score)
        }.toMap()
    }

    private fun backpropagate(leaf: Node, winner: List<Int>) {
        var current: Node? = leaf
        while (current != null) {
            current.visited(winner)
            current = current.parent
        }
    }

    private fun expand(leaf: Node): Node {
        val possibleMoves = leaf.game.board.legalMoves(game.getBoardCurrentPlayer() == 0)
        if (possibleMoves.isEmpty()) {
            return leaf
        }

        val possibleNodes = possibleMoves.mapNotNull { move ->
            try {
                val deep = game.deepCopy()
                deep.makeMove(move)
                leaf.add(move, deep)
            } catch (e: IllegalStateException) {
                null // Skip invalid moves
            }
        }

        return if (possibleNodes.isEmpty()) {
            leaf
        } else {
            expandChoose(possibleNodes)
        }
    }

    private fun expandChoose(nodes: List<Node>): Node {
        return nodes.random()
    }

    private fun select(root: Node): Node {
        var current = root
        while (!current.isLeaf()) {
            // when lambda=0, the algorithm purely focuses on exploitation.
            // when lambda=1, it purely focuses on the heuristic value.
            // 0<lambda<<1 is a blend of both.
            current = current.bestChild(explorationFactor = 2.0, lambda = 0.8)
        }
        return current
    }

    private fun simulate(leaf: Node): List<Int> {
        val currentGame = leaf.game.deepCopy()
        var status = currentGame.status

        while (status !is Game.Status.Finished) {
            val move = simulateChoose(currentGame)
            if (move == -1) {
                break // No legal moves available, end the simulation
            }
            status = currentGame.makeMove(move)
        }

        return when (status) {
            Game.Status.Finished.Draw -> listOf(0, 1)
            Game.Status.Finished.PlayerOneWin -> listOf(0)
            Game.Status.Finished.PlayerTwoWin -> listOf(1)
            else -> listOf() // Handle other statuses as needed
        }
    }


    private fun simulateChoose(currentGame: Game): Int {
        val legalMoves = currentGame.board.legalMoves(currentGame.getBoardCurrentPlayer() == 0)

        if (legalMoves.isEmpty()) {
            return -1 // Indicate no legal moves are available
        }

        val scoredMoves = legalMoves.mapNotNull { move ->
            try {
                val deep = currentGame.deepCopy()
                deep.makeMove(move)
                val score = deep.score(
                    deep.getBoardCurrentPlayer(),
                    // if (deep.board.playerOne.turn) 0 else 1,
                )
                Pair(move, score)
            } catch (e: IllegalStateException) {
                null // Skip invalid moves
            }
        }.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenByDescending { it.first })

//        println()
//        println("position:")
//        currentGame.board.printBoard()
//        println("scored moves:")
//        scoredMoves.forEach {
//            println("${it.first} : ${it.second}")
//        }
//        println()
//        println("picked move: ${scoredMoves.first().first}")
//        println()

        if (scoredMoves.isEmpty()) {
            return -1 // Indicate no scored moves are available
        }

        return scoredMoves.first().first // Pick the move with the highest score
    }
}
