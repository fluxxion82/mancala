@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ai.sterling.loading

import kotlinx.coroutines.CompletableDeferred

/**
 * URL the wasmJs target fetches the gzipped weights from. Relative so it works
 * for both the dev server and the production static deploy, both of which place
 * Compose Resources at the same path next to the JS bundle.
 */
private const val WEIGHTS_GZ_URL =
    "composeResources/ai.sterling.mancala.resources/files/mancala_weights.bin.gz"

private const val IDB_NAME = "mancala-weights"
private const val IDB_STORE = "weights"

/* -------- JS interop -------- */

@JsFun(
    """
    (version, onHit, onMiss) => {
        try {
            const req = indexedDB.open('mancala-weights', 1);
            req.onupgradeneeded = (ev) => {
                const db = ev.target.result;
                if (!db.objectStoreNames.contains('weights')) {
                    db.createObjectStore('weights');
                }
            };
            req.onerror = () => onMiss('open-error');
            req.onsuccess = (ev) => {
                try {
                    const db = ev.target.result;
                    if (!db.objectStoreNames.contains('weights')) { onMiss('no-store'); db.close(); return; }
                    const tx = db.transaction('weights', 'readonly');
                    const store = tx.objectStore('weights');
                    const getReq = store.get(version);
                    getReq.onsuccess = () => {
                        const v = getReq.result;
                        db.close();
                        if (v && v.byteLength) onHit(v);
                        else onMiss('miss');
                    };
                    getReq.onerror = () => { db.close(); onMiss('get-error'); };
                } catch (e) {
                    onMiss('tx-throw');
                }
            };
        } catch (e) {
            onMiss('open-throw');
        }
    }
    """,
)
private external fun idbGet(
    version: String,
    onHit: (JsAny) -> Unit,
    onMiss: (String) -> Unit,
)

@JsFun(
    """
    (version, u8) => {
        try {
            const req = indexedDB.open('mancala-weights', 1);
            req.onupgradeneeded = (ev) => {
                const db = ev.target.result;
                if (!db.objectStoreNames.contains('weights')) {
                    db.createObjectStore('weights');
                }
            };
            req.onerror = () => {};
            req.onsuccess = (ev) => {
                try {
                    const db = ev.target.result;
                    if (!db.objectStoreNames.contains('weights')) { db.close(); return; }
                    const tx = db.transaction('weights', 'readwrite');
                    const store = tx.objectStore('weights');
                    store.put(u8, version);
                    tx.oncomplete = () => db.close();
                    tx.onerror = () => db.close();
                    tx.onabort = () => db.close();
                } catch (e) {}
            };
        } catch (e) {}
    }
    """,
)
private external fun idbPut(version: String, u8: JsAny)

@JsFun(
    """
    (url, onProgress, onDone, onError) => {
        (async () => {
            try {
                const resp = await fetch(url);
                if (!resp.ok) { onError('http ' + resp.status); return; }
                const lenHdr = resp.headers.get('content-length');
                const total = lenHdr ? Number(lenHdr) : -1;
                const reader = resp.body.getReader();
                const chunks = [];
                let received = 0;
                while (true) {
                    const r = await reader.read();
                    if (r.done) break;
                    chunks.push(r.value);
                    received += r.value.byteLength;
                    onProgress(received, total);
                }
                let merged;
                if (chunks.length === 1) {
                    merged = chunks[0];
                } else {
                    merged = new Uint8Array(received);
                    let offset = 0;
                    for (const c of chunks) { merged.set(c, offset); offset += c.byteLength; }
                }
                onDone(merged, received);
            } catch (e) {
                onError(e && e.message ? e.message : String(e));
            }
        })();
    }
    """,
)
private external fun fetchBytes(
    url: String,
    onProgress: (Int, Int) -> Unit,
    onDone: (JsAny, Int) -> Unit,
    onError: (String) -> Unit,
)

@JsFun(
    """
    (u8, onDone, onError) => {
        (async () => {
            try {
                const blob = new Blob([u8]);
                const decompressed = blob.stream().pipeThrough(new DecompressionStream('gzip'));
                const buf = await new Response(decompressed).arrayBuffer();
                onDone(new Uint8Array(buf), buf.byteLength);
            } catch (e) {
                onError(e && e.message ? e.message : String(e));
            }
        })();
    }
    """,
)
private external fun gunzip(u8: JsAny, onDone: (JsAny, Int) -> Unit, onError: (String) -> Unit)

@JsFun("(u8, idx) => u8[idx]")
private external fun readByteAt(u8: JsAny, idx: Int): Int

@JsFun("(size) => new Uint8Array(size)")
private external fun newUint8Array(size: Int): JsAny

@JsFun("(u8, idx, value) => { u8[idx] = value; }")
private external fun writeByteAt(u8: JsAny, idx: Int, value: Int)

@JsFun("(u8) => u8.byteLength")
private external fun u8Length(u8: JsAny): Int

/* -------- Coroutine bridges -------- */

private suspend fun idbLookup(version: String): ByteArray? {
    val deferred = CompletableDeferred<JsAny?>()
    idbGet(
        version = version,
        onHit = { u8 -> deferred.complete(u8) },
        onMiss = { _ -> deferred.complete(null) },
    )
    val u8 = deferred.await() ?: return null
    return jsBytesToKotlin(u8)
}

private suspend fun fetchWithProgress(
    url: String,
    onBytes: (Long, Long?) -> Unit,
): ByteArray {
    val deferred = CompletableDeferred<JsAny>()
    fetchBytes(
        url = url,
        onProgress = { received, total ->
            val totalOrNull = if (total < 0) null else total.toLong()
            onBytes(received.toLong(), totalOrNull)
        },
        onDone = { u8, _ -> deferred.complete(u8) },
        onError = { msg -> deferred.completeExceptionally(RuntimeException("fetch failed: $msg")) },
    )
    return jsBytesToKotlin(deferred.await())
}

private suspend fun decompressGzip(compressed: ByteArray): ByteArray {
    val u8In = kotlinBytesToJs(compressed)
    val deferred = CompletableDeferred<JsAny>()
    gunzip(
        u8 = u8In,
        onDone = { u8, _ -> deferred.complete(u8) },
        onError = { msg -> deferred.completeExceptionally(RuntimeException("gunzip failed: $msg")) },
    )
    return jsBytesToKotlin(deferred.await())
}

private fun saveToIdbBestEffort(version: String, bytes: ByteArray) {
    try {
        val u8 = kotlinBytesToJs(bytes)
        idbPut(version, u8)
    } catch (_: Throwable) {
        // Best-effort cache write; never block the user.
    }
}

private fun jsBytesToKotlin(u8: JsAny): ByteArray {
    val n = u8Length(u8)
    val out = ByteArray(n)
    var i = 0
    while (i < n) {
        out[i] = readByteAt(u8, i).toByte()
        i++
    }
    return out
}

private fun kotlinBytesToJs(bytes: ByteArray): JsAny {
    val n = bytes.size
    val u8 = newUint8Array(n)
    var i = 0
    while (i < n) {
        writeByteAt(u8, i, bytes[i].toInt() and 0xFF)
        i++
    }
    return u8
}

/* -------- Public actual -------- */

internal actual suspend fun fetchWeightBytes(
    version: String,
    onState: (WeightLoadingState) -> Unit,
): ByteArray {
    onState(WeightLoadingState.Checking)
    runCatching { idbLookup(version) }
        .getOrNull()
        ?.let {
            onState(WeightLoadingState.Initializing)
            return it
        }

    onState(WeightLoadingState.Downloading(received = 0L, total = null))
    val gz = fetchWithProgress(WEIGHTS_GZ_URL) { received, total ->
        onState(WeightLoadingState.Downloading(received = received, total = total))
    }

    onState(WeightLoadingState.Decompressing)
    val raw = decompressGzip(gz)

    saveToIdbBestEffort(version, raw)

    onState(WeightLoadingState.Initializing)
    return raw
}
