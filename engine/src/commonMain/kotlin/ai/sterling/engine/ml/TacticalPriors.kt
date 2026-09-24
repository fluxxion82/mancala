package ai.sterling.engine.ml

/**
 * Mix a small tactical prior into the network's softmax output so PUCT can't
 * starve an obvious capture / "go again" / store-deposit move when the policy
 * head's prior is near zero.
 *
 * The shape uses the same per-action features the engine encodes for V2's input
 * (`NeuralNetEngine.computeActionFeatures`): a `goAgain` flag (move lands in
 * own mancala), a `capture` amount, and a `storeGain` delta — all per-action,
 * all already normalized by /48.
 *
 * Blend is `0.85 * net + 0.15 * tactical`, then floor every legal move to a
 * minimum of `0.02` and renormalize. PUCT's exploration term is prior-
 * proportional, so a 0.02 floor translates into ~one guaranteed visit per legal
 * move at the root within any reasonable budget. The 15% tactical mass is small
 * enough that a confident, correct policy net stays in charge; large enough
 * that an underconfident or misweighted prior can't starve an obvious tactical
 * move.
 *
 * @param priors net's softmaxed (legal-masked) prior, mutated in place.
 * @param actionFeatures 18-element FloatArray laid out as
 *   `[goAgain_0, capture_0, storeGain_0, goAgain_1, ...]` per action 0..5.
 *   See [NeuralNetEngine.computeActionFeatures].
 * @param legalRel the relative-action indices (0..5) currently legal.
 */
internal fun blendTacticalPrior(
    priors: FloatArray,
    actionFeatures: FloatArray,
    legalRel: List<Int>,
) {
    if (legalRel.size <= 1) return

    // Score per action: chain (binary 1.0) + 1.5 * capture-amount (0..~0.3) +
    // store-gain (0..~0.15). The chain flag is intentionally on a different scale
    // than the /48-normalized capture and store-gain features — a chain that
    // keeps the turn is roughly always more valuable than a single capture, and
    // we want chains to dominate the tactical term. The 1.5x multiplier on
    // capture vs store-gain reflects that captures both gain stones AND deny the
    // opponent.
    val tactical = FloatArray(6)
    var tacticalSum = 0f
    for (rel in legalRel) {
        val o = rel * 3
        val score = actionFeatures[o] /* go-again */ +
            1.5f * actionFeatures[o + 1] /* capture */ +
            actionFeatures[o + 2] /* store delta */
        val safe = if (score < 0f) 0f else score
        tactical[rel] = safe
        tacticalSum += safe
    }
    if (tacticalSum > 0f) {
        for (rel in legalRel) tactical[rel] /= tacticalSum
    } else {
        // No tactical signal: fall back to uniform over legal so the floor still
        // protects every legal move.
        val u = 1f / legalRel.size
        for (rel in legalRel) tactical[rel] = u
    }

    for (rel in legalRel) {
        priors[rel] = 0.85f * priors[rel] + 0.15f * tactical[rel]
    }

    val floor = 0.02f
    var sum = 0f
    for (rel in legalRel) {
        if (priors[rel] < floor) priors[rel] = floor
        sum += priors[rel]
    }
    if (sum > 0f) {
        for (rel in legalRel) priors[rel] /= sum
    }
}
