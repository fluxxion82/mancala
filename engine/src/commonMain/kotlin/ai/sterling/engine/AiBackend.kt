package ai.sterling.engine

import ai.sterling.AiMode
import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.model.Game

/**
 * Platform-neutral AI backend. The default implementation runs the engine in-process;
 * on Wasm we additionally provide a [WorkerEngineHost][ai.sterling.worker.WorkerEngineHost]
 * implementation that proxies calls into a Web Worker so the inference loop can run
 * off the UI thread.
 */
interface AiBackend {
    suspend fun selectMove(game: Game, mode: AiMode): Int

    /**
     * Drop any cached cross-move search state (e.g. PUCT root, transposition
     * table). Called by the repository on game restart so the next search
     * starts from a clean slate. Default is a no-op for stateless backends.
     */
    fun resetSearchState() {}

    /**
     * Telemetry from the most recent [selectMove] call (priors, visits, value
     * head, cache stats, …). `null` if the backend doesn't surface telemetry or
     * if no move has been searched yet. Read by the repository immediately after
     * [selectMove] returns; backends are not expected to keep this stable past
     * the next call.
     */
    fun lastSearchTelemetry(): MoveTelemetry? = null
}

/**
 * Adapts a [NeuralNetEngine] to the [AiBackend] interface by dispatching on [AiMode].
 * This is the only [AiBackend] used on JVM/desktop; on Wasm it lives inside the worker.
 */
class InProcessAiBackend(private val engine: NeuralNetEngine) : AiBackend {
    override suspend fun selectMove(game: Game, mode: AiMode): Int = when (mode) {
        is AiMode.AlphaBeta ->
            if (mode.timeBudgetMs > 0L) {
                engine.selectMoveAdaptive(game, mode.timeBudgetMs, mode.maxDepth)
            } else {
                engine.selectMove(game)
            }
        is AiMode.Mcts -> engine.selectMoveMcts(
            game = game,
            simulations = mode.simulations,
            timeBudgetMs = mode.timeBudgetMs,
            cPuct = mode.cPuct,
            neuralWeight = mode.neuralWeight,
            openingTempPlies = mode.openingTempPlies,
            openingTemperature = mode.openingTemperature,
        )
    }

    override fun resetSearchState() = engine.resetSearchState()

    override fun lastSearchTelemetry(): MoveTelemetry? = engine.lastTelemetry
}

fun NeuralNetEngine.asBackend(): AiBackend = InProcessAiBackend(this)
