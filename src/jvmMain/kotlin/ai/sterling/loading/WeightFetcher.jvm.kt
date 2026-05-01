package ai.sterling.loading

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.mancala.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * JVM/desktop reads the uncompressed weights resource directly. The desktop binary
 * is bundled, so there's no network and no value in gzip — just hand back the bytes.
 */
@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun fetchWeightBytes(
    version: String,
    onState: (WeightLoadingState) -> Unit,
): ByteArray {
    onState(WeightLoadingState.Initializing)
    return Res.readBytes(NeuralNetEngine.WEIGHTS_RESOURCE_PATH)
}
