package ai.sterling.engine.ml

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readFloatLe
import kotlinx.io.readIntLe
import kotlinx.io.readShortLe
import kotlinx.io.readTo
import kotlinx.io.writeFloatLe
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe

data class ModelWeights(
    val arrays: Map<String, FloatArray>,
    val strings: Map<String, String>,
)

// Flat binary "MCLW" weight blob — see writeWeights() for layout.
//   u32 magic ("MCLW") | u32 version | u32 numFloatArrays | u32 numStrings
//   per float array:  u16 nameLen, name (UTF-8), u32 length, length × f32 LE
//   per string:       u16 nameLen, name (UTF-8), u32 byteLen, byteLen bytes (UTF-8)
private const val MAGIC = 0x4D434C57
private const val FORMAT_VERSION = 1

fun parseWeights(bytes: ByteArray): ModelWeights {
    val buf = Buffer().also { it.write(bytes) }
    val magic = buf.readIntLe()
    require(magic == MAGIC) {
        "Bad weight blob magic: 0x${magic.toUInt().toString(16)} (expected 0x${MAGIC.toUInt().toString(16)})"
    }
    val version = buf.readIntLe()
    require(version == FORMAT_VERSION) { "Unsupported weight blob version: $version" }
    val numFloatArrays = buf.readIntLe()
    val numStrings = buf.readIntLe()

    val arrays = HashMap<String, FloatArray>(numFloatArrays)
    repeat(numFloatArrays) {
        val name = buf.readShortString()
        val length = buf.readIntLe()
        val data = FloatArray(length) { buf.readFloatLe() }
        arrays[name] = data
    }
    val strings = HashMap<String, String>(numStrings)
    repeat(numStrings) {
        val name = buf.readShortString()
        val byteLen = buf.readIntLe()
        val raw = ByteArray(byteLen)
        buf.readTo(raw)
        strings[name] = raw.decodeToString()
    }
    return ModelWeights(arrays, strings)
}

fun writeWeights(arrays: Map<String, FloatArray>, strings: Map<String, String>): ByteArray {
    val buf = Buffer()
    buf.writeIntLe(MAGIC)
    buf.writeIntLe(FORMAT_VERSION)
    buf.writeIntLe(arrays.size)
    buf.writeIntLe(strings.size)
    for ((name, arr) in arrays) {
        buf.writeShortString(name)
        buf.writeIntLe(arr.size)
        for (v in arr) buf.writeFloatLe(v)
    }
    for ((name, value) in strings) {
        buf.writeShortString(name)
        val raw = value.encodeToByteArray()
        buf.writeIntLe(raw.size)
        buf.write(raw)
    }
    return buf.readByteArray()
}

private fun Buffer.readShortString(): String {
    val len = readShortLe().toInt() and 0xFFFF
    val raw = ByteArray(len)
    readTo(raw)
    return raw.decodeToString()
}

private fun Buffer.writeShortString(s: String) {
    val raw = s.encodeToByteArray()
    require(raw.size <= 0xFFFF) { "Name too long for u16 length prefix: $s (${raw.size} bytes)" }
    writeShortLe(raw.size.toShort())
    write(raw)
}
