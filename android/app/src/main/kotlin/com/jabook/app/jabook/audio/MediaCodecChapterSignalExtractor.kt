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

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Default ChapterSignalExtractor implementation.
 *
 * Extracts a coarse RMS envelope (in dB) by decoding short windows distributed
 * across the whole track.
 */
@Singleton
internal class MediaCodecChapterSignalExtractor
    @Inject
    constructor(
        loggerFactory: LoggerFactory,
    ) : ChapterSignalExtractor {
        private val logger = loggerFactory.get("MediaCodecChapterSignalExtractor")

        override suspend fun extractRmsDb(
            filePath: String,
            windowStepMs: Long,
        ): List<Float> {
            if (windowStepMs <= 0L) return emptyList()
            val file = File(filePath)
            if (!file.exists() || !file.isFile) return emptyList()

            val extractor = MediaExtractor()
            var codec: MediaCodec? = null

            return try {
                extractor.setDataSource(file.absolutePath)
                val audioTrackIndex = findAudioTrackIndex(extractor) ?: return emptyList()
                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)

                val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
                val sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(0)
                val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(0)
                if (durationUs <= 0L || sampleRate <= 0 || channels <= 0) return emptyList()

                codec = createAndConfigureDecoder(format) ?: return emptyList()
                val durationMs = durationUs / 1000L
                val samplingPlan =
                    ChapterDetectionSamplingPolicy.buildPlan(
                        durationMs = durationMs,
                        requestedWindowStepMs = windowStepMs,
                    )
                val windowsToProcess = samplingPlan.windowsToProcess
                val targetSamplesPerChannel =
                    ((sampleRate * samplingPlan.effectiveWindowStepMs) / 1000L).coerceAtLeast(1L).toInt()
                val result = ArrayList<Float>(windowsToProcess)

                for (windowIndex in 0 until windowsToProcess) {
                    val positionUs = ((windowIndex.toLong() * durationUs) / windowsToProcess.toLong()).coerceAtLeast(0L)
                    extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    val pcmSamples =
                        decodeWindow(
                            extractor = extractor,
                            codec = codec,
                            channels = channels,
                            targetSamplesPerChannel = targetSamplesPerChannel,
                        )
                    val rmsDb = pcmSamples?.let { rmsDb(it) } ?: SILENCE_FLOOR_DB
                    result.add(rmsDb)
                    codec.flush()
                }

                logger.d {
                    "Extracted chapter RMS windows: file=${file.name} windows=$windowsToProcess " +
                        "requestedStepMs=$windowStepMs effectiveStepMs=${samplingPlan.effectiveWindowStepMs}"
                }
                result
            } catch (e: IOException) {
                logger.w(e) { "Chapter signal extraction I/O failure for ${file.name}" }
                emptyList()
            } catch (e: IllegalArgumentException) {
                logger.w(e) { "Chapter signal extraction failed with invalid media format for ${file.name}" }
                emptyList()
            } catch (e: MediaCodec.CodecException) {
                logger.w(e) { "Chapter signal extraction codec failure for ${file.name}" }
                emptyList()
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                extractor.release()
            }
        }

        private fun createAndConfigureDecoder(format: MediaFormat): MediaCodec? =
            try {
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                codec
            } catch (_: Exception) {
                null
            }

        private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) return i
            }
            return null
        }

        private fun decodeWindow(
            extractor: MediaExtractor,
            codec: MediaCodec,
            channels: Int,
            targetSamplesPerChannel: Int,
        ): ShortArray? {
            val outputBufferInfo = MediaCodec.BufferInfo()
            val pcm = ArrayList<Short>(targetSamplesPerChannel * channels)
            var buffersProcessed = 0
            var noProgressCounter = 0

            while (pcm.size < targetSamplesPerChannel * channels && buffersProcessed < MAX_BUFFERS_PER_WINDOW) {
                var madeProgress = false
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize >= 0) {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                            madeProgress = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            madeProgress = true
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(outputBufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && outputBufferInfo.size > 0) {
                        outputBuffer.position(outputBufferInfo.offset)
                        outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        while (outputBuffer.hasRemaining() && pcm.size < targetSamplesPerChannel * channels) {
                            pcm.add(outputBuffer.short)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    buffersProcessed++
                    madeProgress = true
                }

                if ((outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                if (madeProgress) {
                    noProgressCounter = 0
                } else {
                    noProgressCounter++
                    if (noProgressCounter > MAX_NO_PROGRESS_DEQUEUE_ATTEMPTS) break
                }
            }

            return if (pcm.isNotEmpty()) pcm.toShortArray() else null
        }

        private fun rmsDb(samples: ShortArray): Float {
            if (samples.isEmpty()) return SILENCE_FLOOR_DB
            var sumSquares = 0.0
            samples.forEach { sample ->
                val normalized = sample / 32768.0
                sumSquares += normalized * normalized
            }
            val rms = sqrt(sumSquares / samples.size.toDouble())
            if (rms <= 0.0) return SILENCE_FLOOR_DB
            val db = 20.0 * log10(rms)
            return db.toFloat().coerceAtLeast(SILENCE_FLOOR_DB)
        }

        private companion object {
            private const val MAX_BUFFERS_PER_WINDOW: Int = 50
            private const val MAX_NO_PROGRESS_DEQUEUE_ATTEMPTS: Int = 10
            private const val DEQUEUE_TIMEOUT_US: Long = 10_000L
            private const val SILENCE_FLOOR_DB: Float = -96f
        }
    }
