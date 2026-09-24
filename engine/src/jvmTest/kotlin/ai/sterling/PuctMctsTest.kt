package ai.sterling

import ai.sterling.engine.ml.PuctMcts
import ai.sterling.model.Board
import ai.sterling.model.Game
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests target [PuctMcts] directly with a synthetic policy/value callback so we don't
 * need to load the 20 MB model in unit tests. The algorithm correctness — particularly
 * the same-player backup rule for mancala chain moves — is verifiable independently of
 * the network weights.
 */
class PuctMctsTest {

    /**
     * Two calls with the same simulation count must produce the same move. AlphaZero
     * applies Dirichlet noise + temperature only during *self-play training*; at
     * inference we want deterministic, strongest-move selection.
     */
    @Test
    fun determinismWithFixedSimulations() = runBlocking {
        val game = Game.new()
        val mcts = PuctMcts(policyValue = ::uniformZeroEval)

        val first = mcts.selectMove(game, simulations = 128)
        val second = mcts.selectMove(game, simulations = 128)

        assertEquals(first, second, "MCTS must be deterministic at inference")
    }

    /**
     * Wall-clock budget must be respected. Allow a generous margin for JVM warmup +
     * GC; the point is that the call returns and doesn't loop forever.
     */
    @Test
    fun timeBudgetExits() = runBlocking {
        val game = Game.new()
        val mcts = PuctMcts(policyValue = ::uniformZeroEval)

        val start = System.currentTimeMillis()
        mcts.selectMove(game, timeBudgetMs = 50L)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 500L, "MCTS overran time budget by too much: ${elapsed}ms")
    }

    /**
     * The Mancala-specific wrinkle: when a move ends in the player's own mancala the
     * same player keeps the turn. The parent and child nodes share their mover's POV,
     * so the value MUST NOT be negated when crossing that edge during backup.
     *
     * Reproduces the bug class: a fake [policyValue] that returns a strong positive
     * value only when "it's P1's turn AND P1 is ahead". Pit 2 from the initial board
     * lands in P1's mancala (chain). After that move P1's mancala is 1 (P1 is "ahead"),
     * and it's still P1's turn. So at the post-pit-2 leaf the eval returns +1.
     *
     * With correct same-player backup: that +1 reaches root.w[2] unchanged. Pit 2 has
     * the highest mean-Q and wins by visit count.
     *
     * With a buggy backup that flips on every edge: the +1 becomes -1 at root, and pit
     * 2 becomes the *worst* move. Test would fail.
     */
    @Test
    fun sameMoverEdgeDoesNotFlipValue() = runBlocking {
        val game = Game.new()
        val mcts = PuctMcts(policyValue = ::rewardP1AheadOnP1Turn)
        val move = mcts.selectMove(game, simulations = 256)

        assertEquals(2, move, "MCTS should prefer the chain-starting move at pit 2")
    }

    /**
     * Sanity check: a position with one obviously-winning terminal move. The board is
     * crafted so that playing pit 5 sweeps the board with P1 way ahead; PUCT should
     * pick it regardless of priors because the terminal outcome dominates.
     */
    @Test
    fun terminalWinIsPicked() = runBlocking {
        // P1's only stone is at pit 5. Playing it lands in P1's mancala (chain), then
        // P1's side becomes empty — the standard end-of-game sweep gives all of P2's
        // remaining stones to P2. P1 mancala goes 30 → 31; P2 mancala goes 0 → 18.
        // P1 wins. Other "moves" don't exist (no other P1 pit has stones).
        val pockets = listOf(
            0, 0, 0, 0, 0, 1,   // P1 pits
            30,                  // P1 mancala
            6, 6, 6, 0, 0, 0,    // P2 pits
            0,                   // P2 mancala (init block requires this)
        )
        // Total stones = 1 + 30 + 18 = 49 — but the board enforces 48. Adjust.
        val pockets48 = listOf(
            0, 0, 0, 0, 0, 1,
            29,
            6, 6, 6, 0, 0, 0,
            0,
        )
        val game = Game.newGameWithPosition(pockets48, isPlayerOneTurn = true)
        val mcts = PuctMcts(policyValue = ::adversarialEval)

        // Even with an adversarial eval that says "all non-terminal positions are bad
        // for P1", the terminal win value (+1) at the leaf must dominate.
        val move = mcts.selectMove(game, simulations = 64)
        assertEquals(5, move, "MCTS must pick the terminal winning move at pit 5")
    }

    /**
     * MCTS-Solver: when a child reaches a terminal state, its `provenValue` should
     * be set immediately — and the search loop should propagate that proof up so
     * the root knows the move at pit 5 is a guaranteed win.
     *
     * The same forced-win position from [terminalWinIsPicked]: pit 5 is the only
     * legal move, ends in P1's mancala (chain), then sweeps to a P1 win.
     */
    @Test
    fun terminalWinProofIsPropagated() = runBlocking {
        val pockets48 = listOf(0, 0, 0, 0, 0, 1, 29, 6, 6, 6, 0, 0, 0, 0)
        val game = Game.newGameWithPosition(pockets48, isPlayerOneTurn = true)
        val mcts = PuctMcts(policyValue = ::adversarialEval)
        val root = mcts.freshRoot(game)
        mcts.search(root, simulations = 32)

        val winChild = root.children[5]
        assertNotNull(winChild, "MCTS should have visited pit 5 at least once")
        assertEquals(
            1f,
            winChild.provenValue,
            "Terminal-win child should be proven +1 from its own mover's POV",
        )
        // Once a child is proven a forced win for the parent's mover, the parent
        // is itself proven (the mover would simply play that move).
        assertEquals(
            1f,
            root.provenValue,
            "Parent should be proven +1 once any child is a forced win",
        )
    }

    /**
     * Tree reuse: after a search expands the root and several children, locating the
     * post-move state from the previous root should return the *same* PuctNode instance
     * (with all its accumulated visit counts), not a fresh allocation. This is the
     * mechanism that lets [NeuralNetEngine.selectMoveMcts] inherit prior turns' work.
     */
    @Test
    fun locateOrAdvanceFindsExpandedChild() = runBlocking {
        val game = Game.new()
        val mcts = PuctMcts(policyValue = ::uniformZeroEval)
        val root = mcts.freshRoot(game)
        mcts.search(root, simulations = 64)

        // Pick the most-visited child — guaranteed to have been expanded.
        var bestRel = -1
        var bestN = -1
        for (rel in 0 until 6) {
            if (!root.legalRel[rel]) continue
            if (root.n[rel] > bestN) {
                bestN = root.n[rel]
                bestRel = rel
            }
        }
        assertTrue(bestRel >= 0 && bestN > 0, "MCTS should have visited at least one legal child")

        val absMove = if (root.isPlayerOne) bestRel else bestRel + 7
        val nextGame = game.makeMove(absMove)

        val located = mcts.locateOrAdvance(root, nextGame)
        assertNotNull(located, "Should locate the cached subtree under the chosen move")
        assertSame(
            root.children[bestRel],
            located,
            "locateOrAdvance must return the cached child instance, not a fresh PuctNode",
        )
        assertTrue(
            located.visitCount > 0,
            "Reused subtree must carry over its accumulated visit count",
        )
    }

    /**
     * locateOrAdvance returns null when the cached tree doesn't contain the target
     * state — e.g. when the cached tree's children haven't been expanded into the
     * subtree the opponent's move leads to. The simplest case: a root with no
     * search performed (children all null), then ask for any post-move state.
     */
    @Test
    fun locateOrAdvanceReturnsNullWhenStateNotInTree() = runBlocking {
        val game = Game.new()
        val mcts = PuctMcts(policyValue = ::uniformZeroEval)
        val root = mcts.freshRoot(game)
        // No simulations — only the root is expanded; its children are still null.

        val nextGame = game.makeMove(2) // legal P1 move from the initial board
        val located = mcts.locateOrAdvance(root, nextGame)
        assertEquals(
            null,
            located,
            "Should return null when the post-move state isn't in the cached tree",
        )
    }
}

/** Uniform priors, value 0 — a no-information eval. */
private fun uniformZeroEval(@Suppress("UNUSED_PARAMETER") board: Board, @Suppress("UNUSED_PARAMETER") isPlayerOne: Boolean): Pair<FloatArray, Float> =
    FloatArray(6) { 1f / 6f } to 0f

/**
 * Returns +1 from the current mover's POV when "P1 is ahead AND it's P1's turn".
 * Used to verify that values from a same-player edge propagate without sign flip.
 */
private fun rewardP1AheadOnP1Turn(board: Board, isPlayerOne: Boolean): Pair<FloatArray, Float> {
    val priors = FloatArray(6) { 1f / 6f }
    val p1Ahead = board.playerOne.mancala > board.playerTwo.mancala
    val value = if (isPlayerOne && p1Ahead) 1f else 0f
    return priors to value
}

/**
 * Always returns -1 from the current mover's POV (i.e., "current state is terrible").
 * The terminal-win test uses this to confirm that the +1 from a *terminal* leaf
 * dominates the network's pessimistic non-terminal estimate.
 */
private fun adversarialEval(@Suppress("UNUSED_PARAMETER") board: Board, @Suppress("UNUSED_PARAMETER") isPlayerOne: Boolean): Pair<FloatArray, Float> =
    FloatArray(6) { 1f / 6f } to -1f
