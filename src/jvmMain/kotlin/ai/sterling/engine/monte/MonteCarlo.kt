package ai.sterling.engine.monte

import ai.sterling.engine.monte.PositionEvaluator.evaluatePosition
import ai.sterling.engine.monte.test.EvalWeights
import ai.sterling.model.Game
import ai.sterling.model.Game.GameStatus

class MonteCarlo(game: Game, private val weights: EvalWeights = EvalWeights()) {
    private var rootNode = Node(game)

    fun apply(game: Game) {
        rootNode = Node(game)
    }

    fun runBest(iterations: Int): Int {
        return run(iterations).maxByOrNull { it.value }?.key ?: -1
    }

    private fun run(iterations: Int): Map<Int, Double> {
        repeat(iterations) {
            val leaf = select(rootNode).let { selected ->
                if (selected.game.status !is GameStatus.Finished) expand(selected) else selected
            }
            val winners = simulate(leaf)
            backpropagate(leaf, winners)
        }

        return rootNode.children.mapValues { (_, node) ->
            node.winCount / node.visitCount
        }
    }

    private fun select(node: Node): Node {
        var current = node
        while (!current.isLeaf && current.game.status !is GameStatus.Finished) {
            current = current.getBestChild()
        }
        return current
    }

    private fun expand(leaf: Node): Node {
        val legalMoves = when (leaf.game.status) {
            is GameStatus.PlayerOneTurn -> leaf.game.board.legalMoves(true)
            is GameStatus.PlayerTwoTurn -> leaf.game.board.legalMoves(false)
            is GameStatus.Finished -> emptyList()
        }

        if (legalMoves.isEmpty()) return leaf

        val children = legalMoves.mapNotNull { move ->
            try {
                val newGame = leaf.game.makeMove(move)
                move to leaf.addChild(move, newGame)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val scoringMove = children.find { (move, node) ->
            val isPlayerOne = node.game.status == GameStatus.PlayerOneTurn
            val position = evaluatePosition(node.game, isPlayerOne, weights)
            position > 5000
        }

        return scoringMove?.second ?: (children.randomOrNull()?.second ?: leaf)
    }

    private fun simulate(node: Node): List<Int> {
        var currentGame = node.game

        while (currentGame.status !is GameStatus.Finished) {
            val isPlayerOne = currentGame.status == GameStatus.PlayerOneTurn
            val move = selectSimulationMove(currentGame, isPlayerOne) ?: break

            try {
                currentGame = currentGame.makeMove(move)
            } catch (e: IllegalArgumentException) {
                break
            }
        }

        return when (currentGame.status) {
            is GameStatus.Finished.Draw -> listOf(0, 1)
            is GameStatus.Finished.PlayerOneWin -> listOf(0)
            is GameStatus.Finished.PlayerTwoWin -> listOf(1)
            else -> emptyList()
        }
    }

    private fun selectSimulationMove(game: Game, isPlayerOne: Boolean): Int? {
        val legalMoves = game.board.legalMoves(isPlayerOne)
        if (legalMoves.isEmpty()) return null

        return legalMoves.maxByOrNull { move ->
            try {
                val newGame = game.makeMove(move)
                evaluatePosition(newGame, isPlayerOne, weights)
            } catch (e: IllegalArgumentException) {
                Double.NEGATIVE_INFINITY
            }
        }
    }

    private fun backpropagate(leaf: Node, winners: List<Int>) {
        var current: Node? = leaf
        while (current != null) {
            current.recordVisit(winners)
            current = current.parent
        }
    }
}