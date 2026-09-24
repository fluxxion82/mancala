package ai.sterling

import ai.sterling.engine.ml.NeuralNetEngine
import ai.sterling.loading.readWeightBytes
import java.io.File

/** Weights path injected by engine/build.gradle.kts (the UI module's composeResources copy). */
internal val testWeightBytes: ByteArray by lazy {
    val path = requireNotNull(System.getProperty("mancala.weights")) {
        "mancala.weights system property not set (see engine/build.gradle.kts)"
    }
    readWeightBytes(File(path))
}

internal suspend fun testEngine(searchDepth: Int): NeuralNetEngine =
    NeuralNetEngine.create(searchDepth = searchDepth, weightBytes = testWeightBytes)
