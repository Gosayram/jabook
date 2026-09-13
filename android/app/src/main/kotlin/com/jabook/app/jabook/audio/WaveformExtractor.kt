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
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "WaveformExtractor"

/** Emit a partial waveform snapshot roughly every 60 media-seconds of decoded audio. */
private const val PARTIAL_EMISSION_MEDIA_US: Long = 60_000_000L

/** Dequeue timeout for the synchronous MediaCodec loop. */
private const val DEQUEUE_TIMEOUT_US: Long = 10_000L

/**
 * Extracts whole-file waveform peaks with a single synchronous MediaCodec decode pass.
 *
 * Selects the first audio track, decodes it to PCM, max-pools absolute sample
 * amplitudes into per-second buckets and normalizes the result to 0..1. Partial
 * snapshots are emitted during decoding so the UI can draw the bar filling
 * left-to-right (extraction of a 10h file can take minutes by design).
 */
public class WaveformExtractor(
    private val context: Context,
) {
    /**
     * Returns per-second peak amplitudes normalized 0..1, or empty on failure.
     *
     * @param path File path (or `content://` URI string) of the audio file
     * @param durationMs Expected duration used to pre-size the buckets; when `<= 0`
     *   the bucket count is derived from the last presentation timestamp instead.
     * @param onPartial Invoked with a fresh normalized snapshot every
     *   ~60 media-seconds; the receiver owns the array. Invocations stop once the
     *   coroutine is cancelled.
     */
    public suspend fun extract(
        path: String,
        durationMs: Long,
        onPartial: (FloatArray) -> Unit = {},
    ): FloatArray =
        withContext(Dispatchers.Default) {
            val extractor = MediaExtractor()
            try {
                when {
                    path.startsWith("content:") -> {
                        extractor.setDataSource(context, Uri.parse(path), null)
                    }
                    path.startsWith("http://") || path.startsWith("https://") -> {
                        // ponytail: remote streams never get a waveform — whole-file MediaCodec
                        // decode would burn CPU for the whole download; add streaming support
                        // only if a feature ever needs it
                        return@withContext FloatArray(0)
                    }
                    else -> {
                        val uri = Uri.parse(path)
                        // MediaExtractor.setDataSource(String) expects a local path —
                        // "file://…" URIs (URL-encoded) fail to open, so decode them first
                        val localPath = if (uri.scheme == "file") uri.path ?: path else path
                        extractor.setDataSource(localPath)
                    }
                }

                var trackIndex = -1
                var trackFormat: MediaFormat? = null
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        trackIndex = index
                        trackFormat = format
                        break
                    }
                }
                val mime = trackFormat?.getString(MediaFormat.KEY_MIME)
                if (trackIndex < 0 || trackFormat == null || mime == null) {
                    return@withContext FloatArray(0)
                }
                extractor.selectTrack(trackIndex)

                var peaks = FloatArray((durationMs / 1_000L + 1L).coerceAtLeast(1L).toInt())
                var runningMax = 0f
                var lastEmissionUs = 0L

                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(trackFormat, null, null, 0)
                    codec.start()

                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    while (!outputDone) {
                        coroutineContext.ensureActive()

                        if (!inputDone) {
                            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                            if (inputIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputIndex)
                                val sampleSize = if (inputBuffer == null) -1 else extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0,
                                    )
                                    extractor.advance()
                                }
                            }
                        }

                        val outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                        if (outputIndex >= 0) {
                            if (info.size > 0) {
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                if (outputBuffer != null) {
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    val peak = bufferPcmPeak(outputBuffer)
                                    // ponytail: PCM16-only peak; float-PCM decoders yield noisy-but-bounded bars,
                                    // add KEY_PCM_ENCODING handling if a format ever needs exact bars
                                    val second = (info.presentationTimeUs / 1_000_000L).coerceAtLeast(0L).toInt()
                                    if (second >= peaks.size) {
                                        peaks = peaks.copyOf(max(second + 1, peaks.size * 2))
                                    }
                                    if (peak > peaks[second]) peaks[second] = peak
                                    if (peak > runningMax) runningMax = peak
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }

                            if (info.presentationTimeUs - lastEmissionUs >= PARTIAL_EMISSION_MEDIA_US) {
                                lastEmissionUs = info.presentationTimeUs
                                onPartial(normalizedCopy(peaks, runningMax))
                            }
                        }
                    }
                } finally {
                    try {
                        codec.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        codec.release()
                    } catch (_: Exception) {
                    }
                }

                if (runningMax <= 0f) return@withContext FloatArray(0)
                normalizedCopy(peaks, runningMax)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.e(TAG, "Waveform extraction failed for $path", e)
                FloatArray(0)
            } finally {
                try {
                    extractor.release()
                } catch (_: Exception) {
                }
            }
        }

    private fun bufferPcmPeak(buffer: ByteBuffer): Float {
        val samples = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var peak = 0f
        while (samples.hasRemaining()) {
            val amplitude = abs(samples.get().toInt()) / 32768f
            if (amplitude > peak) peak = amplitude
        }
        return peak
    }

    private fun normalizedCopy(
        peaks: FloatArray,
        maxAmplitude: Float,
    ): FloatArray {
        val result = FloatArray(peaks.size)
        if (maxAmplitude > 0f) {
            for (index in peaks.indices) {
                result[index] = (peaks[index] / maxAmplitude).coerceIn(0f, 1f)
            }
        }
        return result
    }
}
