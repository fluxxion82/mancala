package ai.sterling

import ai.sterling.engine.AiBackend
import ai.sterling.engine.asBackend
import ai.sterling.engine.ml.NeuralNetEngine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Wasm actual: prefer the host's [MancalaBackendFactory.override] (typically a Web
 * Worker proxy installed by the surrounding site), and fall back to in-process if
 * none is configured. The override is set in commonMain — the host doesn't need
 * platform-specific glue to install a worker.
 */
internal actual suspend fun createAiBackend(weightBytes: ByteArray): AiBackend {
    MancalaBackendFactory.override?.let { return it(weightBytes) }
    return NeuralNetEngine.create(searchDepth = 1, weightBytes = weightBytes).asBackend()
}

/**
 * No-op context on Wasm — the platform is single-threaded, so dispatching to
 * `Dispatchers.Default` would still land on the main event loop. Off-main work
 * happens via `MancalaBackendFactory.override` (Web Worker proxy).
 */
internal actual val aiDispatcher: CoroutineContext = EmptyCoroutineContext
