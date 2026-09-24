package ai.sterling.engine.ml

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

data class NpzData(
    val arrays: Map<String, FloatArray>,
    val strings: Map<String, String>
)

/**
 * Reads NumPy .npz (zip of .npy) files into FloatArrays and strings.
 */
object NpyReader {

    fun loadNpz(inputStream: InputStream): NpzData {
        val arrays = mutableMapOf<String, FloatArray>()
        val strings = mutableMapOf<String, String>()
        val zip = ZipInputStream(inputStream)

        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".npy")) {
                val name = entry.name.removeSuffix(".npy")
                val bytes = zip.readBytes()
                val header = parseHeader(bytes)
                if (header.isString) {
                    strings[name] = readNpyString(bytes, header)
                } else {
                    arrays[name] = readNpy(bytes, header)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        zip.close()

        return NpzData(arrays, strings)
    }

    private class NpyHeader(val dataOffset: Int, val dtype: String) {
        val isString get() = dtype.contains("U") || dtype.contains("S")
        val isInt64 get() = dtype.contains("i8")
        val isInt32 get() = dtype.contains("i4")
    }

    private fun parseHeader(bytes: ByteArray): NpyHeader {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Magic: \x93NUMPY
        val magic = ByteArray(6)
        buf.get(magic)
        require(magic[0] == 0x93.toByte() && String(magic, 1, 5) == "NUMPY") {
            "Not a valid .npy file"
        }

        val major = buf.get().toInt() and 0xFF
        buf.get() // minor

        val headerLen = when (major) {
            1 -> (buf.short.toInt() and 0xFFFF)
            2 -> buf.int
            else -> error("Unsupported .npy version $major")
        }

        val headerStart = buf.position()
        val headerStr = String(bytes, headerStart, headerLen)

        // Extract dtype from header, e.g. 'descr': '<f4'
        val descrMatch = Regex("""'descr':\s*'([^']*)'""").find(headerStr)
        val dtype = descrMatch?.groupValues?.get(1) ?: "<f4"

        return NpyHeader(headerStart + headerLen, dtype)
    }

    private fun readNpyString(bytes: ByteArray, header: NpyHeader): String {
        val buf = ByteBuffer.wrap(bytes, header.dataOffset, bytes.size - header.dataOffset)
            .order(ByteOrder.LITTLE_ENDIAN)
        val dataBytes = bytes.size - header.dataOffset

        // NumPy Unicode strings (<U) use 4 bytes per character (UCS-4)
        val charCount = dataBytes / 4
        val sb = StringBuilder()
        for (i in 0 until charCount) {
            val codePoint = buf.int
            if (codePoint == 0) break // null terminator
            sb.appendCodePoint(codePoint)
        }
        return sb.toString()
    }

    private fun readNpy(bytes: ByteArray, header: NpyHeader): FloatArray {
        val buf = ByteBuffer.wrap(bytes, header.dataOffset, bytes.size - header.dataOffset)
            .order(ByteOrder.LITTLE_ENDIAN)
        val dataBytes = bytes.size - header.dataOffset

        if (header.isInt64) {
            val count = dataBytes / 8
            return FloatArray(count) { buf.long.toFloat() }
        }
        if (header.isInt32) {
            val count = dataBytes / 4
            return FloatArray(count) { buf.int.toFloat() }
        }

        val count = dataBytes / 4
        return FloatArray(count) { buf.float }
    }
}
