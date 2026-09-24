package ai.sterling.loading

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * JVM weight loading without Compose Resources. Accepts either the raw
 * `mancala_weights.bin` or the gzipped `mancala_weights.bin.gz` (detected by the gzip
 * magic bytes, not the file name) and returns the uncompressed bytes ready for
 * [ai.sterling.createAiBackend] / `NeuralNetEngine.create`.
 *
 * The canonical copies live in the UI module at
 * `src/commonMain/composeResources/files/mancala_weights.bin{,.gz}`; the website serves
 * the `.gz` at `/composeResources/ai.sterling.mancala.resources/files/mancala_weights.bin.gz`.
 */
fun readWeightBytes(file: File): ByteArray = gunzipIfNeeded(file.readBytes())

/** Same as [readWeightBytes] for bytes already in memory (e.g. an HTTP download). */
fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
    val isGzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
    if (!isGzip) return bytes
    return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}
