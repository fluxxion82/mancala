package ai.sterling

import ai.sterling.engine.ml.blendTacticalPrior
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the standalone tactical-prior blender. These exercise the "easy points"
 * fix without needing to load the 20MB model — the blender takes both the priors
 * and the precomputed action features as inputs, so we can synthesize hostile
 * priors and confirm they get rescued.
 *
 * Action features layout (per action 0..5, three slots each):
 *   [0] go-again flag  (1.0 if move ends in own mancala)
 *   [1] capture amount (stones captured / 48)
 *   [2] store gain     (stones added to own mancala this move / 48)
 */
class TacticalPriorsTest {

    private val legalAll = listOf(0, 1, 2, 3, 4, 5)

    @Test
    fun blendingNeverDropsLegalMoveBelowFloor() {
        // Adversarial prior: 0.95 mass on pit 0, 0.01 each on 1-5.
        val priors = floatArrayOf(0.95f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f)
        val emptyFeatures = FloatArray(18) // no tactical signal anywhere

        blendTacticalPrior(priors, emptyFeatures, legalAll)

        val floor = 0.02f
        for (rel in legalAll) {
            assertTrue(
                priors[rel] >= floor - 1e-6f,
                "Legal move $rel should be at least floor=$floor after blending, was ${priors[rel]}",
            )
        }
        assertSumsToOne(priors, legalAll)
    }

    @Test
    fun chainMoveGetsLifted() {
        // Hostile prior pushes all mass onto pit 0 (no tactical value); pit 2 has
        // a "go again" flag set in features. After blending the pit-2 prior should
        // be substantially higher than the unflagged moves.
        val priors = floatArrayOf(0.95f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f)
        val features = FloatArray(18).apply {
            this[2 * 3 + 0] = 1f // pit 2 = go again
        }

        blendTacticalPrior(priors, features, legalAll)

        val baseline = listOf(1, 3, 4, 5).map { priors[it] }.average().toFloat()
        assertTrue(
            priors[2] > baseline * 2f,
            "Chain-flagged move (pit 2) should clear at least 2× the baseline of unflagged legal moves; pit2=${priors[2]} baseline=$baseline",
        )
        assertSumsToOne(priors, legalAll)
    }

    @Test
    fun captureMoveOutscoresStoreGainOfSameMagnitude() {
        // Capture is weighted 1.5× store-gain. Verify: pit 1 gets capture=0.2,
        // pit 4 gets store-gain=0.2, both with otherwise-equal hostile priors.
        // After blending, pit 1 should be strictly larger than pit 4.
        val priors = FloatArray(6) { 1f / 6f } // uniform — neutral prior
        val features = FloatArray(18).apply {
            this[1 * 3 + 1] = 0.2f // pit 1 captures
            this[4 * 3 + 2] = 0.2f // pit 4 deposits stones in own store
        }

        blendTacticalPrior(priors, features, legalAll)

        assertTrue(
            priors[1] > priors[4],
            "Capture (pit 1) should outweigh equal-magnitude store gain (pit 4); pit1=${priors[1]} pit4=${priors[4]}",
        )
        assertSumsToOne(priors, legalAll)
    }

    @Test
    fun forcedMoveLeavesPriorsUntouched() {
        // Single-legal-move position: blender should bail out without modifying
        // priors (the upstream caller already takes the forced-move shortcut).
        val priors = floatArrayOf(0.5f, 0.5f, 0f, 0f, 0f, 0f)
        val original = priors.copyOf()
        val features = FloatArray(18).apply {
            this[0 * 3 + 0] = 1f // even with a strong tactical signal
        }

        blendTacticalPrior(priors, features, legalRel = listOf(0))

        for (i in priors.indices) {
            assertEquals(original[i], priors[i], 1e-6f, "Single-legal-move position should not be reshaped")
        }
    }

    @Test
    fun illegalMovesStayAtZero() {
        // Only pits 0, 2, 4 are legal. Pit 5 has a strong go-again signal in the
        // (unused) features slot. After blending, illegal moves must remain 0.
        val priors = floatArrayOf(0.6f, 0f, 0.3f, 0f, 0.1f, 0f)
        val features = FloatArray(18).apply {
            this[5 * 3 + 0] = 1f
            this[2 * 3 + 0] = 1f // pit 2 go again — should be lifted
        }
        val legal = listOf(0, 2, 4)

        blendTacticalPrior(priors, features, legal)

        assertEquals(0f, priors[1], "Illegal move 1 must remain 0")
        assertEquals(0f, priors[3], "Illegal move 3 must remain 0")
        assertEquals(0f, priors[5], "Illegal move 5 must remain 0 even when feature flagged")
        assertSumsToOne(priors, legal)
    }

    private fun assertSumsToOne(priors: FloatArray, legal: List<Int>) {
        val sum = legal.sumOf { priors[it].toDouble() }.toFloat()
        assertTrue(abs(sum - 1f) < 1e-3f, "Legal priors should sum to 1, got $sum")
    }
}
