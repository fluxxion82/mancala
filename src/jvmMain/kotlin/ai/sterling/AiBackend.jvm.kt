package ai.sterling

import ai.sterling.engine.AiBackend
import ai.sterling.engine.asBackend
import ai.sterling.engine.ml.NeuralNetEngine
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** JVM/desktop runs the engine in-process — real threads, no need for a worker. */
internal actual suspend fun createAiBackend(weightBytes: ByteArray): AiBackend =
    NeuralNetEngine.create(searchDepth = 1, weightBytes = weightBytes).asBackend()

/**
 * Single-lane background dispatcher. The engine's persistent search tree
 * (`NeuralNetEngine.currentRoot`) and transposition table are mutable single-threaded
 * state, so we pin one worker thread; the ViewModel's `collectLatest` already
 * serializes calls in practice but `limitedParallelism(1)` makes the contract
 * explicit and future-proof.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal actual val aiDispatcher: CoroutineContext = Dispatchers.Default.limitedParallelism(1)
