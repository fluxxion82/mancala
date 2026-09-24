package ai.sterling.loading

/**
 * Wasm: IndexedDB cache keyed by the weights hash, else fetch the gzipped Compose
 * Resource next to the JS bundle and inflate it (see the engine's [downloadWeightBytes]).
 */
internal actual suspend fun fetchWeightBytes(
    version: String,
    onState: (WeightLoadingState) -> Unit,
): ByteArray = downloadWeightBytes(url = MANCALA_WEIGHTS_GZ_PATH, cacheKey = version, onState = onState)
