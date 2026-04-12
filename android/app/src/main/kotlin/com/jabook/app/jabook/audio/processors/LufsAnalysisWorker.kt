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

package com.jabook.app.jabook.audio.processors

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.crash.CrashDiagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WorkManager-based worker for background loudness analysis of audiobooks.
 *
 * This worker:
 * 1. Reads audio samples from the file via [MediaExtractor]
 * 2. Estimates LUFS using [LufsRmsEstimator]
 * 3. Persists the result via [BooksDao.updateLufsValue]
 *
 * ## Sampling strategy
 *
 * To keep analysis time bounded, the worker reads at most [MAX_SAMPLE_DURATION_MS]
 * of audio data, evenly distributed across the file. This is sufficient for
 * a reliable LUFS estimate of typical audiobook content.
 *
 * ## Scheduling
 *
 * Schedule via WorkManager with unique work name format: `lufs_analysis_{bookId}`.
 */
@HiltWorker
public class LufsAnalysisWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val booksDao: BooksDao,
        private val loggerFactory: LoggerFactory,
    ) : CoroutineWorker(context, params) {
        private val logger = loggerFactory.get("LufsAnalysisWorker")

        public companion object {
            /** Input data key for the book ID to analyze. */
            public const val KEY_BOOK_ID: String = "book_id"

            /** Output data key for the computed LUFS value. */
            public const val KEY_LUFS_VALUE: String = "lufs_value"

            /** Output data key for the analyzed file path. */
            public const val KEY_FILE_PATH: String = "file_path"

            /**
             * Maximum duration of audio to sample for LUFS estimation, in milliseconds.
             *
             * Reading ~5 minutes of audio distributed across the file provides a
             * reliable estimate for audiobook content while keeping analysis fast.
             */
            internal const val MAX_SAMPLE_DURATION_MS: Long = 5 * 60 * 1000L

            /**
             * Number of sampling windows to distribute across the file.
             * Each window is [MAX_SAMPLE_DURATION_MS] / [SAMPLE_WINDOWS] ms long.
             */
            internal const val SAMPLE_WINDOWS: Int = 10

            /** Target sample count per analysis chunk (roughly 1 second at 44.1kHz). */
            internal const val TARGET_SAMPLES_PER_CHUNK: Int = 44_100

            /** Maximum number of sample data buffers to read per chunk. */
            private const val MAX_BUFFERS_PER_CHUNK: Int = 50

            /** Supported audio file extensions for LUFS analysis. */
            private val SUPPORTED_EXTENSIONS =
                setOf(
                    "mp3",
                    "m4a",
                    "m4b",
                    "ogg",
                    "opus",
                    "flac",
                    "wav",
                    "aac",
                    "wma",
                )
        }

        override suspend fun doWork(): Result {
            val bookId =
                inputData.getString(KEY_BOOK_ID)
                    ?: return Result.failure()

            return try {
                val lufsValue =
                    withContext(Dispatchers.IO) {
                        analyzeBookLoudness(bookId)
                    }

                if (lufsValue != null) {
                    booksDao.updateLufsValue(bookId, lufsValue)
                    logger.i { "LUFS analysis complete for book=$bookId: $lufsValue LUFS" }
                    Result.success(
                        workDataOf(
                            KEY_BOOK_ID to bookId,
                            KEY_LUFS_VALUE to lufsValue,
                        ),
                    )
                } else {
                    logger.w { "LUFS analysis returned null for book=$bookId (likely unsupported format)" }
                    Result.failure(
                        workDataOf(
                            KEY_BOOK_ID to bookId,
                            "error" to "Could not estimate LUFS",
                        ),
                    )
                }
            } catch (e: CancellationException) {
                logger.i { "LUFS analysis cancelled for book=$bookId" }
                throw e
            } catch (e: Exception) {
                logger.e({ "LUFS analysis failed for book=$bookId" }, e)
                CrashDiagnostics.reportNonFatal(
                    tag = "lufs_analysis_failure",
                    throwable = e,
                    attributes =
                        mapOf("book_id" to bookId),
                )
                Result.failure(
                    workDataOf(
                        KEY_BOOK_ID to bookId,
                        "error" to (e.message ?: "Unknown error"),
                    ),
                )
            }
        }

        /**
         * Analyzes the loudness of the first audio file found in the book's local directory.
         *
         * Uses distributed sampling: divides the file into [SAMPLE_WINDOWS] windows and reads
         * a short chunk from each window, then integrates the LUFS estimates.
         *
         * @return estimated LUFS value, or null if analysis could not be performed
         */
        internal suspend fun analyzeBookLoudness(bookId: String): Double? {
            val localPath = booksDao.getBookLocalPath(bookId) ?: return null
            val audioFile = findFirstAudioFile(localPath) ?: return null

            return estimateLufsForFile(audioFile)
        }

        /**
         * Finds the first supported audio file in the given directory (or the file itself).
         */
        internal fun findFirstAudioFile(path: String): File? {
            val file = File(path)
            if (!file.exists()) return null

            if (file.isFile && file.isSupportedAudioFile()) return file

            if (file.isDirectory) {
                return file
                    .listFiles()
                    ?.filter { it.isSupportedAudioFile() }
                    ?.minByOrNull { it.name }
            }
            return null
        }

        /**
         * Performs distributed LUFS estimation on a single audio file.
         *
         * Reads [SAMPLE_WINDOWS] evenly-spaced chunks and integrates the results
         * using energy-weighted averaging via [LufsRmsEstimator.integrateEstimates].
         *
         * Uses [MediaCodec] to decode compressed audio formats (MP3, AAC, etc.) into
         * PCM before estimating LUFS, ensuring accurate loudness measurement.
         */
        internal fun estimateLufsForFile(audioFile: File): Double? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            return try {
                extractor.setDataSource(audioFile.absolutePath)
                val audioTrackIndex = findAudioTrackIndex(extractor) ?: return null
                extractor.selectTrack(audioTrackIndex)

                val format = extractor.getTrackFormat(audioTrackIndex)
                val durationUs =
                    runCatching { format.getLong(MediaFormat.KEY_DURATION) }
                        .getOrDefault(0L)
                val sampleRate =
                    runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                        .getOrDefault(0)
                val channels =
                    runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                        .getOrDefault(0)

                if (durationUs <= 0 || sampleRate <= 0 || channels <= 0) return null

                // Create and configure MediaCodec decoder
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val windowSizeUs = durationUs / SAMPLE_WINDOWS
                val chunkSizeSamples = TARGET_SAMPLES_PER_CHUNK
                val estimates = mutableListOf<Double>()

                for (windowIndex in 0 until SAMPLE_WINDOWS) {
                    val seekPositionUs = windowIndex * windowSizeUs
                    extractor.seekTo(seekPositionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    val pcmBuffer = mutableListOf<Short>()
                    var buffersProcessed = 0
                    val inputBufferInfo = MediaCodec.BufferInfo()

                    // Decode samples for this window
                    while (pcmBuffer.size < chunkSizeSamples * channels && buffersProcessed < MAX_BUFFERS_PER_CHUNK) {
                        // Feed input to decoder
                        val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize >= 0) {
                                    val presentationTimeUs = extractor.sampleTime
                                    codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                                    extractor.advance()
                                } else {
                                    // End of stream
                                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                }
                            }
                        }

                        // Get decoded output
                        val outputBufferIndex = codec.dequeueOutputBuffer(inputBufferInfo, 10_000)
                        if (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && inputBufferInfo.size > 0) {
                                outputBuffer.position(inputBufferInfo.offset)
                                outputBuffer.limit(inputBufferInfo.offset + inputBufferInfo.size)
                                // Convert ByteBuffer to ShortArray (PCM 16-bit)
                                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                while (outputBuffer.hasRemaining() && pcmBuffer.size < chunkSizeSamples * channels) {
                                    pcmBuffer.add(outputBuffer.short)
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            buffersProcessed++
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // Output format changed, continue
                        }

                        if ((inputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }

                    // Estimate LUFS for this window
                    if (pcmBuffer.size > channels) {
                        val estimate =
                            LufsRmsEstimator.estimateLufsFromPcm16(
                                pcm16Data = pcmBuffer.toShortArray(),
                                channels = channels,
                            )
                        if (estimate != null) {
                            estimates.add(estimate)
                        }
                    }

                    // Reset codec for next window
                    codec.flush()
                }

                LufsRmsEstimator.integrateEstimates(estimates)
            } catch (e: IOException) {
                logger.w({ "Could not read audio file: ${audioFile.absolutePath}" }, e)
                null
            } finally {
                codec?.stop()
                codec?.release()
                extractor.release()
            }
        }

        /**
         * Finds the index of the first audio track in the media file.
         */
        private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) return i
            }
            return null
        }

        /**
         * Checks if the file has a supported audio extension.
         */
        private fun File.isSupportedAudioFile(): Boolean {
            val ext = extension.lowercase()
            return ext in SUPPORTED_EXTENSIONS
        }
    }