package ai.sterling.engine.monte.test

import ai.sterling.engine.monte.MonteCarlo
import ai.sterling.model.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class EvalWeights(
    val scoringMove: Double = 6110.773644912023, // 5133.355397607918,
    val captureMove: Double = 1001.3035221078235, // 2394.2192738520253,
    val captureStoneValue: Double = 334.2387177411762, // 112.4413953998069,
    val blockCapture: Double = 4030.1555199060695, // 2053.9169685861925,
    val rightmostBase: Double = 2853.340471978067, // 972.7127932091412,
    val rightmostDecay: Double = 133.57048605207873, // 105.28510802292752,
)

class PositionTester {
    private val testPositions = listOf(
        TestPosition(
            pockets = mutableListOf(4, 4, 0, 5, 5, 0, 2, 5, 5, 5, 5, 4, 4, 0),
            isPlayerOneMove = false,
            correctMove = 8,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(4, 4, 4, 4, 4, 4, 0, 4, 4, 4, 4, 4, 4, 0),
            isPlayerOneMove = true,
            correctMove = 2,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(4, 4, 0, 5, 5, 5, 1, 4, 4, 4, 4, 4, 4, 0),
            isPlayerOneMove = true,
            correctMove = 5,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(5, 5, 0, 5, 5, 0, 2, 5, 5, 5, 5, 0, 5, 1),
            isPlayerOneMove = true,
            correctMove = 1,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(5, 0, 1, 6, 6, 1, 3, 5, 5, 5, 5, 0, 5, 1),
            isPlayerOneMove = true,
            correctMove = 5,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(5, 0, 1, 6, 6, 0, 4, 5, 5, 5, 5, 0, 5, 1),
            isPlayerOneMove = true,
            correctMove = 4,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(6, 1, 2, 6, 0, 1, 5, 6, 6, 6, 0, 1, 6, 2),
            isPlayerOneMove = true,
            correctMove = 5,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(6, 1, 2, 6, 0, 0, 6, 6, 6, 6, 0, 1, 6, 2),
            isPlayerOneMove = true,
            correctMove = 2,
            description = "capture"
        ),
        TestPosition(
            pockets = mutableListOf(6, 1, 0, 0, 1, 1, 14, 7, 0, 8, 1, 0, 7, 2),
            isPlayerOneMove = true,
            correctMove = 5,
            description = "immediate score"
        ),
        TestPosition(
            pockets = mutableListOf(6, 1, 0, 0, 1, 0, 15, 7, 0, 8, 1, 0, 7, 2),
            isPlayerOneMove = true,
            correctMove = 4,
            description = "biggest capture" // there's another capture but for less
        ),
        TestPosition(
            pockets = mutableListOf(5, 0, 1, 6, 6, 0, 4, 5, 5, 5, 0, 5, 5, 1),
            isPlayerOneMove = true,
            correctMove = 4,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(6, 1, 2, 7, 0, 1, 5, 6, 6, 6, 1, 5, 0, 2),
            isPlayerOneMove = true,
            correctMove = 5,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(6, 1, 2, 7, 0, 0, 6, 6, 6, 6, 1, 5, 0, 2),
            isPlayerOneMove = true,
            correctMove = 0,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(1, 3, 0, 0, 2, 1, 18, 1, 7, 7, 2, 0, 3, 3),
            isPlayerOneMove = true,
            correctMove = 5,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(1, 3, 0, 0, 2, 0, 19, 1, 7, 7, 2, 0, 3, 3),
            isPlayerOneMove = true,
            correctMove = 4,
            description = "Should find immediate scoring move"
        ),
        TestPosition(
            pockets = mutableListOf(1, 3, 0, 0, 0, 0, 21, 1, 7, 7, 2, 0, 3, 3),
            isPlayerOneMove = true,
            correctMove = 1,
            description = "capture"
        ),
        TestPosition(
            pockets = mutableListOf(1, 0, 1, 1, 0, 0, 29, 1, 0, 7, 2, 0, 3, 3),
            isPlayerOneMove = false,
            correctMove = 12,
            description = "capture"
        ),
    )

    suspend fun findOptimalWeights(
        iterations: Int = 5000,
        populationSize: Int = 50,
        numGenerations: Int = 10,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    ): EvalWeights {
        var bestWeights = EvalWeights()
        var bestScore = evaluateWeights(bestWeights)
        val random = Random(System.currentTimeMillis())

        repeat(iterations) { iteration ->
            repeat(numGenerations) { generation ->
                val population = generatePopulation(bestWeights, populationSize, random)

                val results = population.map { weights ->
                    scope.async {
                        weights to evaluateWeights(weights)
                    }
                }.awaitAll()

                val (newBestWeights, newBestScore) = results.maxByOrNull { it.second }!!

                if (newBestScore > bestScore) {
                    bestScore = newBestScore
                    bestWeights = newBestWeights
                    println("Iteration $iteration, Generation $generation: New best weights found!")
                    println("Score: $bestScore")
                    println("Weights: $bestWeights")
                    printPositionResults(bestWeights)
                }
            }
        }

        return bestWeights
    }

    private fun generatePopulation(
        baseWeights: EvalWeights,
        size: Int,
        random: Random
    ): List<EvalWeights> {
        return List(size) {
            if (random.nextDouble() < 0.3) {
                generateRandomWeights(random)
            } else {
                mutateWeights(baseWeights, random)
            }
        }
    }

    private fun generateRandomWeights(random: Random): EvalWeights {
        return EvalWeights(
            scoringMove = random.nextDouble(1000.0, 10000.0),
            captureMove = random.nextDouble(1000.0, 8000.0),
            captureStoneValue = random.nextDouble(50.0, 500.0),
            blockCapture = random.nextDouble(1000.0, 6000.0),
            rightmostBase = random.nextDouble(500.0, 3000.0),
            rightmostDecay = random.nextDouble(50.0, 300.0)
        )
    }

    private fun mutateWeights(weights: EvalWeights, random: Random): EvalWeights {
        val mutationChance = 0.4
        val mutationRange = 0.3

        fun mutateIfSelected(value: Double): Double {
            return if (random.nextDouble() < mutationChance) {
                value * (1.0 + (random.nextDouble() - 0.5) * mutationRange * 2)
            } else {
                value
            }
        }

        return EvalWeights(
            scoringMove = mutateIfSelected(weights.scoringMove),
            captureMove = mutateIfSelected(weights.captureMove),
            captureStoneValue = mutateIfSelected(weights.captureStoneValue),
            blockCapture = mutateIfSelected(weights.blockCapture),
            rightmostBase = mutateIfSelected(weights.rightmostBase),
            rightmostDecay = mutateIfSelected(weights.rightmostDecay)
        )
    }

    private suspend fun printPositionResults(weights: EvalWeights) {
        testPositions.forEachIndexed { index, position ->
            val moveSelected = findBestMove(position.pockets, position.isPlayerOneMove, weights)
            println("Position ${index + 1}: ${if (moveSelected == position.correctMove) "SOLVED" else "FAILED"}")
            println("  Selected move: $moveSelected, Expected: ${position.correctMove}")
            println("  Description: ${position.description}")
        }
    }

    private suspend fun evaluateWeights(weights: EvalWeights): Double = coroutineScope {
        var score = 0.0

        val results = testPositions.map { position ->
            async {
                val moveSelected = findBestMove(position.pockets, position.isPlayerOneMove, weights)
                if (moveSelected == position.correctMove) 1.0 else 0.0
            }
        }.awaitAll()

        score = results.sum()
        score + Random.nextDouble() * 0.01
    }

    private suspend fun findBestMove(
        pockets: List<Int>,
        isPlayerOneTurn: Boolean,
        weights: EvalWeights
    ): Int = withContext(Dispatchers.Default) {
        val game = Game.newGameWithPosition(pockets.toMutableList(), isPlayerOneTurn)
        val engine = MonteCarlo(game, weights)
        engine.runBest(50000)
    }
}

data class TestPosition(
    val pockets: MutableList<Int>,
    val isPlayerOneMove: Boolean,
    val correctMove: Int,
    val description: String
)

suspend fun main() {
    val tester = PositionTester()
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    try {
        val optimalWeights = tester.findOptimalWeights(
            iterations = 100,
            populationSize = 50,
            numGenerations = 10,
            scope = scope
        )
        println("Final optimal weights: $optimalWeights")
    } finally {
        scope.cancel()
    }
}
