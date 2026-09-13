// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.audio

import android.content.Context
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val TAG = "WaveformCache"

private const val CACHE_DIR_NAME = "waveform_cache"

/** On-disk format version byte. Bump when the layout changes. */
internal const val WAVEFORM_CACHE_FORMAT_VERSION: Byte = 1

/** Version byte + 4-byte bucket count header, followed by one quantized byte per peak. */
private const val WAVEFORM_CACHE_HEADER_SIZE = 5

private const val WAVEFORM_MEMORY_CACHE_MAX_ENTRIES = 8

/**
 * Disk + memory cache for extracted whole-file waveforms.
 *
 * Keys hash path length, path identity, file size and last-modified so a changed
 * or replaced file never serves a stale waveform. Every operation fails soft:
 * the cache must never crash playback.
 */
public class WaveformCache(
    context: Context,
) {
    private val cacheDir = File(context.filesDir, CACHE_DIR_NAME)

    private val memoryCache =
        object : LinkedHashMap<String, FloatArray>(WAVEFORM_MEMORY_CACHE_MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>): Boolean =
                size > WAVEFORM_MEMORY_CACHE_MAX_ENTRIES
        }

    /**
     * Stable cache key for [path], or null when the key cannot be derived.
     */
    public fun cacheKey(path: String): String? =
        try {
            val file = File(path)
            val raw = "${path.length}_${path.hashCode()}_${file.length()}_${file.lastModified()}"
            sha256Hex(raw)
        } catch (_: Exception) {
            null
        }

    /** Returns the cached peaks, or null on miss/corruption/IO failure. */
    public suspend fun load(key: String): FloatArray? =
        withContext(Dispatchers.IO) {
            try {
                synchronized(memoryCache) { memoryCache[key] }?.let { return@withContext it }
                val file = File(cacheDir, key)
                if (!file.isFile) return@withContext null
                val peaks = decodePeaks(file.readBytes())
                if (peaks != null) {
                    synchronized(memoryCache) { memoryCache[key] = peaks }
                }
                peaks
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.w(TAG, "Waveform cache load failed", e)
                null
            }
        }

    /** Persists [peaks]; failures are logged and ignored. */
    public suspend fun save(
        key: String,
        peaks: FloatArray,
    ): Unit =
        withContext(Dispatchers.IO) {
            try {
                synchronized(memoryCache) { memoryCache[key] = peaks }
                cacheDir.mkdirs()
                File(cacheDir, key).writeBytes(encodePeaks(peaks))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.w(TAG, "Waveform cache save failed", e)
            }
        }
}

/** Version byte + 4-byte BE bucket count, followed by one quantized byte per peak. */
internal fun encodePeaks(peaks: FloatArray): ByteArray {
    val bytes = ByteArray(WAVEFORM_CACHE_HEADER_SIZE + peaks.size)
    bytes[0] = WAVEFORM_CACHE_FORMAT_VERSION
    bytes[1] = (peaks.size ushr 24).toByte()
    bytes[2] = (peaks.size ushr 16).toByte()
    bytes[3] = (peaks.size ushr 8).toByte()
    bytes[4] = peaks.size.toByte()
    for (index in peaks.indices) {
        bytes[WAVEFORM_CACHE_HEADER_SIZE + index] = (peaks[index].coerceIn(0f, 1f) * 255f).toInt().toByte()
    }
    return bytes
}

/** Inverse of [encodePeaks]; null on any corruption or version mismatch. */
internal fun decodePeaks(bytes: ByteArray): FloatArray? {
    if (bytes.size < WAVEFORM_CACHE_HEADER_SIZE || bytes[0] != WAVEFORM_CACHE_FORMAT_VERSION) return null
    val count =
        ((bytes[1].toInt() and 0xFF) shl 24) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 8) or
            (bytes[4].toInt() and 0xFF)
    if (count <= 0 || bytes.size != WAVEFORM_CACHE_HEADER_SIZE + count) return null
    val peaks = FloatArray(count)
    for (index in 0 until count) {
        peaks[index] = (bytes[WAVEFORM_CACHE_HEADER_SIZE + index].toInt() and 0xFF) / 255f
    }
    return peaks
}

private fun sha256Hex(value: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
