package ai.sterling

import ai.sterling.engine.AiBackend

/**
 * Platform-specific factory: parses [weightBytes] (the uncompressed
 * `mancala_weights.bin`) and returns a ready [AiBackend].
 *
 * - JVM: always runs the engine in-process.
 * - Wasm: uses [MancalaBackendFactory.override] if the host installed one (the website
 *   installs a Web Worker proxy), otherwise runs the engine in-process on the calling
 *   (main) thread.
 */
expect suspend fun createAiBackend(weightBytes: ByteArray): AiBackend

/**
 * CoroutineContext the repository uses to run AI compute. JVM = a single-lane
 * `Dispatchers.Default` (the engine's mutable search tree + TT make concurrent calls
 * unsafe, so we pin one worker thread) so the search doesn't block the UI thread.
 * Wasm = `EmptyCoroutineContext` because the platform has no background threads;
 * the worker actual handles off-main work via `MancalaBackendFactory.override`.
 */
internal expect val aiDispatcher: kotlin.coroutines.CoroutineContext

/**
 * Override hook for the wasm backend factory. The host site sets this at startup
 * (e.g. to plug in a Web Worker host that knows its own bundle URL), and the
 * platform-specific [createAiBackend] consults it before falling back to in-process.
 *
 * Setting this is a no-op on JVM. Kept in commonMain so the call site doesn't need
 * platform-specific initialization code.
 */
public object MancalaBackendFactory {
    /** Set by the host before the first backend is created (e.g. before `MancalaGame` mounts). */
    public var override: (suspend (ByteArray) -> AiBackend)? = null
}
