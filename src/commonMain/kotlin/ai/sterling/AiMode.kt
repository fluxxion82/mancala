package ai.sterling

/**
 * Selects how the AI picks moves at inference time.
 *
 * - [AlphaBeta] uses iterative-deepening alpha-beta search guided by the policy network's
 *   move ordering, with the value head + heuristic blend at leaves. Predictable, fast at
 *   shallow depth, behavior matches what the Mancala project shipped before MCTS landed.
 *
 * - [Mcts] runs an AlphaZero-style PUCT search. Uses the policy head as priors and the
 *   value head at leaves, with no random rollouts. Generally stronger for the same wall-
 *   clock budget once the budget is large enough to run a few hundred simulations, but
 *   has more startup cost than alpha-beta at very tight budgets (<150ms).
 */
sealed class AiMode {
    data class AlphaBeta(
        val timeBudgetMs: Long = 600L,
        val maxDepth: Int = 6,
    ) : AiMode()

    /**
     * [simulations] caps total tree visits; [timeBudgetMs] caps wall-clock per move.
     * Either > 0 enables that limit; if both are 0, defaults to 200 simulations.
     *
     * [cPuct] is the PUCT exploration constant. Lower (≈1.0) trusts the policy
     * network more (exploit-heavy); higher (≈2.0) explores wider. Default 1.5.
     *
     * [neuralWeight] blends the network's value head with the heuristic at leaves:
     * `value = neuralWeight * neural + (1 - neuralWeight) * heuristic`. Default 0.7.
     * Bump toward 0.85+ once the trained value head is reliable; stay near 0.7 if
     * the network is small/early.
     *
     * [openingTempPlies] enables AlphaZero-style temperature sampling on the root
     * visit-count distribution for the first N AI moves of a game, then falls back
     * to the deterministic visit-count argmax. Default 0 = always deterministic
     * (preserves the existing test contract). Set to 3-4 to break repeat-game
     * loops in human-vs-AI play. Proven wins/losses are still selected
     * deterministically — temperature never randomizes a forced result.
     *
     * [openingTemperature] controls the sampling sharpness when the temperature
     * window is active: `P(a) ∝ visits(a)^(1/T)`. T=1.0 picks proportional to
     * visit count (the strongest move still wins most of the time, second-best
     * gets realistic chances). T<1 sharpens, T>1 flattens. Ignored when
     * `openingTempPlies = 0`.
     */
    data class Mcts(
        val simulations: Int = 0,
        val timeBudgetMs: Long = 600L,
        val cPuct: Float = 1.5f,
        val neuralWeight: Float = 0.7f,
        val openingTempPlies: Int = 0,
        val openingTemperature: Float = 1.0f,
    ) : AiMode()
}
