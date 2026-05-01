package ai.sterling.loading

/**
 * Platform-specific weight loader. The `version` argument is the SHA-256 prefix
 * generated at build time (see `MancalaWeightsVersion.kt`) and is used by the
 * wasmJs implementation as an IndexedDB cache key.
 *
 * The implementation drives [onState] through the loading phases. Implementations
 * MUST emit a terminal state — either [WeightLoadingState.Initializing] (which the
 * caller will turn into [WeightLoadingState.Ready] once the engine is up) or
 * [WeightLoadingState.Error] (followed by throwing).
 *
 * Returns the uncompressed weight bytes ready for `WeightsLoader.parseWeights`.
 */
internal expect suspend fun fetchWeightBytes(
    version: String,
    onState: (WeightLoadingState) -> Unit,
): ByteArray
