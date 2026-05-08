package ai.sterling

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.model.Game
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression tests pinning two missed-capture positions from interactive play
 * (recorded in ~/mancala_games.jsonl, games dated 2026-05-07). Both fail
 * against pre-Stage-E NeuralNetEngine (heuristic blend `0.7 * neural + 0.3 *
 * heuristic` with the misleading `0.3 * (mySide - oppSide)` term, no
 * quiescence search). After Stage E (simplified heuristic + lower blend +
 * quiescence search), the [game3_*] tests must pass.
 *
 * Background convention used throughout this file:
 *   pockets indices 0..5  = PlayerOne pits
 *   pockets index   6     = PlayerOne mancala
 *   pockets indices 7..12 = PlayerTwo pits
 *   pockets index   13    = PlayerTwo mancala
 *
 * In the interactive games the human played as PlayerOne and the AI played
 * as PlayerTwo, so the AI's moves are the absolute pit indices 7..12.
 */
class NeuralNetEngineQuiescenceTest {

    /**
     * Game 3 ply 24. The AI (PlayerTwo) has 11 stones in pit 10 (raw=3),
     * directly across from PlayerOne's empty pit 2. Any PlayerOne move that
     * lands a single stone in pit 2 captures all 12+ stones plus the lander.
     *
     * Defensive moves available to the AI:
     *   pit 10 (raw=3): sows the 11-stone pile, captures 3 of PlayerOne's
     *                   stones along the way, and removes the threat.
     *   pit 12 (raw=5): sows 8 stones outward, no capture, but also clears
     *                   the across-pit-2 threat indirectly.
     *
     * The deployed engine pre-Stage-E played pit 9 (raw=2) — sowing only
     * 1 stone into pit 10 (now 12 stones) and exposing the threat fully.
     * PlayerOne replied with pit 1, capturing 13 stones.
     *
     * Python NeuralMiniMax (pure neural value at leaves, no heuristic blend)
     * picks pit 10 at every depth 3-8. Python MCTS picks pit 10 at any sim
     * count. The bug is in Kotlin's leaf evaluator + heuristic blend.
     */
    @Test
    fun game3_ply24_picks_defensive_at_fixed_depth() = runBlocking {
        val pockets = listOf(
            0, 1, 0, 0, 1, 0,    // PlayerOne pits 0..5
            19,                   // PlayerOne mancala
            0, 0, 1, 11, 0, 8,   // PlayerTwo pits 7..12
            7,                    // PlayerTwo mancala
        )
        require(pockets.sum() == 48) { "Test position must sum to 48 stones." }

        val game = Game.newGameWithPosition(pockets, isPlayerOneTurn = false)
        val engine = NeuralNetEngine.create(searchDepth = 6)
        val move = engine.selectMove(game)

        assertTrue(
            move == 10 || move == 12,
            "Expected pit 10 (sow+capture) or pit 12 (sow outward); got pit $move. " +
                "Pre-Stage-E this returns pit 9 and lets PlayerOne capture 13 stones.",
        )
    }

    /**
     * Same position via the deployed-style iterative-deepening path. The
     * deployed engine uses [NeuralNetEngine.selectMoveAdaptive] from
     * [ai.sterling.AiMode.AlphaBeta] — different code path than
     * [selectMove], so we test it explicitly.
     */
    @Test
    fun game3_ply24_picks_defensive_under_time_budget() = runBlocking {
        val pockets = listOf(
            0, 1, 0, 0, 1, 0,
            19,
            0, 0, 1, 11, 0, 8,
            7,
        )
        val game = Game.newGameWithPosition(pockets, isPlayerOneTurn = false)
        val engine = NeuralNetEngine.create(searchDepth = 8)
        // 2-second budget is more than enough on JVM to reach depth 6+ on this
        // shallow-stone position; the deployed engine has 5s on desktop.
        val move = engine.selectMoveAdaptive(game, timeBudgetMs = 2000L, maxDepth = 8)

        assertTrue(
            move == 10 || move == 12,
            "Expected pit 10 or 12 under iterative deepening; got pit $move. " +
                "Pre-Stage-E this returns pit 9.",
        )
    }

    /**
     * Game 2 ply 37. Honest evidence test — non-asserting baseline.
     *
     * Position: PlayerTwo (AI) has [1,0,0,0,9,1] M1=8 vs PlayerOne
     * [1,0,2,0,1,0] M0=25. AI is already losing. The deployed engine played
     * pit 7 (raw=0) which captures 2 stones immediately but lets PlayerOne
     * capture 10 stones in response. The "safer" move is pit 11 (raw=4).
     *
     * Critically, Python NeuralMiniMax PURE NEURAL at depth 5 also picks
     * pit 7 — the model genuinely thinks the immediate +2 is best because
     * its value head is saturated near -1 across all losing continuations.
     * Quiescence search alone CANNOT fix this; it requires Stage D
     * distillation to retrain the value head with stronger teacher targets.
     *
     * This test prints the engine's choice without asserting so we can track
     * how the move shifts across future stages without a hard regression.
     */
    /** Diagnostic: print what each fixed depth picks on the Game 3 ply 24 position. */
    @Test
    fun game3_ply24_diagnostic_per_depth() = runBlocking {
        val pockets = listOf(
            0, 1, 0, 0, 1, 0,
            19,
            0, 0, 1, 11, 0, 8,
            7,
        )
        val game = Game.newGameWithPosition(pockets, isPlayerOneTurn = false)
        println("[Game 3 ply 24 diagnostic] move chosen at each fixed depth:")
        for (d in 1..8) {
            val engine = NeuralNetEngine.create(searchDepth = d)
            val move = engine.selectMove(game)
            val tag = when (move) {
                10, 12 -> "OK (defensive)"
                9 -> "BAD (the deployed bug — sows 1 stone, exposes 12-stone pile)"
                else -> "unexpected"
            }
            println("  depth=$d -> pit $move  $tag")
        }
        val engineAdaptive = NeuralNetEngine.create(searchDepth = 8)
        val moveAdaptive = engineAdaptive.selectMoveAdaptive(game, timeBudgetMs = 2000L, maxDepth = 8)
        println("  selectMoveAdaptive(2000ms, maxDepth=8) -> pit $moveAdaptive")
    }

    @Test
    fun game2_ply37_baseline_no_assert() = runBlocking {
        val pockets = listOf(
            1, 0, 2, 0, 1, 0,
            25,
            1, 0, 0, 0, 9, 1,
            8,
        )
        require(pockets.sum() == 48)

        val game = Game.newGameWithPosition(pockets, isPlayerOneTurn = false)
        val engine = NeuralNetEngine.create(searchDepth = 8)
        val move = engine.selectMoveAdaptive(game, timeBudgetMs = 2000L, maxDepth = 8)

        println(
            "[game2_ply37 baseline] engine chose pit $move " +
                "(pre-Stage-E shipped engine chose pit 7; the safer move would be pit 11). " +
                "This test is documentation only — no assertion.",
        )
    }
}
