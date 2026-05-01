package ai.sterling.engine

/**
 * Per-AI-move debug telemetry. Surfaces what the engine was "thinking":
 *  - search work done (`sims`, `timeMs`, `cacheHits` / `cacheMisses`, `reusedRootVisits`)
 *  - position eval (`rootValue` — the leaf value the search uses for backups, blended
 *    network + heuristic, clamped to [-1, 1])
 *  - decision data (`priors` after tactical shaping, `visits` from the root, `chosenQ`
 *    = mean accumulated value of the picked move)
 *
 * Logged via [ai.sterling.util.GameLogger.recordAiMove] when telemetry is enabled.
 *
 * Reading the data:
 *  - High `rootValue` (close to +1) on a position you're winning suggests the model
 *    is calibrated; high `rootValue` on a losing position suggests the value head is
 *    overconfident.
 *  - `priors` argmax vs `visits` argmax: if they match, the search rubber-stamped
 *    the network's first instinct. If they diverge, MCTS overrode the policy.
 *  - Low `chosenQ` with high `rootValue`: the search's picked line looks worse than
 *    the position estimate — search is finding bad replies.
 */
data class MoveTelemetry(
    /** Absolute pocket index (0-12) of the chosen move. */
    val move: Int,
    /** Total simulations completed (real expansions, not visits to proven nodes). */
    val sims: Int,
    /** Wall-clock ms spent in [ai.sterling.engine.ml.PuctMcts.search]. */
    val timeMs: Long,
    /** Blended (network + heuristic, clamped) leaf value at the root, mover-relative. */
    val rootValue: Float,
    /** 6-element prior at the root after tactical shaping, indexed by relative action. */
    val priors: FloatArray,
    /** 6-element visit count at the root after the search. */
    val visits: IntArray,
    /** Mean accumulated value of the chosen move (`root.w[chosenRel] / root.n[chosenRel]`). */
    val chosenQ: Float,
    /** TT cache hits during this single search call. */
    val cacheHits: Long,
    /** TT cache misses during this single search call. */
    val cacheMisses: Long,
    /** Visit count carried over from the previous search via tree reuse. */
    val reusedRootVisits: Int,
)
