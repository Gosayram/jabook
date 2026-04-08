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

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Lightweight silence skipper for 16-bit PCM streams.
 *
 * Keeps short pauses intact and only drops silence blocks longer than configured threshold.
 * A configurable retain window preserves the last few milliseconds of silence before speech
 * resumes, preventing audible clipping at silence-to-speech transitions.
 *
 * ## Zero-allocation contract (hot path)
 *
 * The [processBuffer] method and its helpers ([copyFrameZeroAlloc], [writeToRetainRing],
 * [flushRetainRing]) never allocate objects on the GC heap. All buffers used in the
 * audio-thread hot loop are pre-allocated in [configure].
 */
@UnstableApi
public class SkipSilenceAudioProcessor(
    private val enabled: Boolean,
    silenceThresholdNormalized: Float,
    minSilenceDurationMs: Int,
    private val mode: SkipSilenceMode = SkipSilenceMode.SKIP,
    retainWindowMs: Int = DEFAULT_RETAIN_WINDOW_MS,
) : AudioProcessor {
    private val thresholdNormalized = silenceThresholdNormalized.coerceIn(0.0001f, 0.1f)
    private val minimumSilenceMs = minSilenceDurationMs.coerceIn(1, 2000)
    private val retainWindowMs = retainWindowMs.coerceIn(MIN_RETAIN_WINDOW_MS, MAX_RETAIN_WINDOW_MS)

    // ---- AudioFormat state ----
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false
    private var bytesPerFrame = 0
    private var minSilenceFrames = 0

    // ---- Buffer management ----
    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var queuedInputBytes = 0
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    // ---- Silence tracking ----
    private var consecutiveSilentFrames = 0
    private var wasDroppingSilence = false

    // ---- Retain window (pre-allocated ring buffer, zero-GC in hot path) ----
    private var retainRing = ByteArray(0)
    private var retainHead = 0
    private var retainBytesAvailable = 0
    private var retainCapacity = 0
    private var retainFrameBytes = 0

    // ---- Zero-allocation temp buffer for frame copying ----
    private var tempFrameBuf = ByteArray(0)

    // ---- Skipped-time metric (cumulative, reset via [resetSkippedMetric]) ----
    private var totalSkippedFrames = 0L

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        bytesPerFrame = (inputAudioFormat.channelCount * PCM_16_BIT_BYTES).coerceAtLeast(PCM_16_BIT_BYTES)
        minSilenceFrames = (inputAudioFormat.sampleRate * minimumSilenceMs / 1000).coerceAtLeast(1)
        consecutiveSilentFrames = 0
        wasDroppingSilence = false
        inputBuffers.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
        totalSkippedFrames = 0L

        // Pre-allocate retain ring buffer (once per configure call, zero-GC during playback)
        val retainFrames = (inputAudioFormat.sampleRate * retainWindowMs / 1000).coerceAtLeast(1)
        retainCapacity = retainFrames * bytesPerFrame
        retainRing = ByteArray(retainCapacity)
        retainHead = 0
        retainBytesAvailable = 0
        retainFrameBytes = bytesPerFrame

        // Pre-allocate temp buffer for zero-allocation frame copying
        tempFrameBuf = ByteArray(bytesPerFrame)

        isActive =
            enabled &&
            inputAudioFormat.encoding == android.media.AudioFormat.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount > 0
        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }
        val copy =
            ByteBuffer
                .allocateDirect(inputBuffer.remaining())
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(inputBuffer)
                    flip()
                }
        inputBuffers.add(copy)
        queuedInputBytes += copy.remaining()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        if (inputBuffers.isEmpty()) {
            return EMPTY_BUFFER
        }

        if (!isActive || queuedInputBytes == 0) {
            val passthrough =
                ByteBuffer.allocateDirect(queuedInputBytes).order(ByteOrder.nativeOrder())
            for (input in inputBuffers) {
                passthrough.put(input)
            }
            inputBuffers.clear()
            queuedInputBytes = 0
            passthrough.flip()
            return passthrough
        }

        val out =
            if (outputBuffer == null || outputBuffer!!.capacity() < queuedInputBytes) {
                ByteBuffer.allocateDirect(queuedInputBytes).order(ByteOrder.nativeOrder()).also {
                    outputBuffer = it
                }
            } else {
                outputBuffer!!.clear()
                outputBuffer
            } ?: return EMPTY_BUFFER

        for (input in inputBuffers) {
            processBuffer(input, out)
        }

        // Flush any remaining retain window (e.g. silence at end of buffer before next speech)
        if (wasDroppingSilence) {
            flushRetainRing(out)
            wasDroppingSilence = false
        }

        inputBuffers.clear()
        queuedInputBytes = 0
        out.flip()
        return out
    }

    /**
     * Core processing loop — zero-allocation hot path.
     *
     * For each PCM frame the method checks whether it is silent (below threshold).
     * Frames within [minSilenceFrames] are always kept. Frames beyond that threshold
     * are either dropped (SKIP) or thinned (SPEED_UP). Dropped frames are buffered
     * into the retain ring so that the last [retainWindowMs] of silence can be
     * restored right before speech resumes, preventing harsh clipping.
     */
    private fun processBuffer(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val channels = inputAudioFormat?.channelCount ?: 1
        val thresholdPcm = (thresholdNormalized * Short.MAX_VALUE).toInt().coerceAtLeast(1)
        val frameCount = input.remaining() / bytesPerFrame

        for (frameIndex in 0 until frameCount) {
            val frameStart = input.position()
            var silentFrame = true
            repeat(channels) {
                val sample = input.short.toInt()
                if (abs(sample) > thresholdPcm) {
                    silentFrame = false
                }
            }
            val frameEnd = input.position()

            if (silentFrame) {
                consecutiveSilentFrames++
                if (consecutiveSilentFrames <= minSilenceFrames) {
                    // Within initial tolerance — keep the frame
                    copyFrameZeroAlloc(input, output, frameStart, frameEnd)
                } else if (mode == SkipSilenceMode.SPEED_UP && shouldKeepFrameInSpeedUpMode()) {
                    // Speed-up mode: keep every Nth frame for time-compression effect
                    copyFrameZeroAlloc(input, output, frameStart, frameEnd)
                } else {
                    // Frame is being dropped — buffer into retain ring for smooth transition
                    writeToRetainRing(input, frameStart, frameEnd)
                    wasDroppingSilence = true
                    totalSkippedFrames++
                }
            } else {
                // Speech detected — flush retain window before the speech frame
                if (wasDroppingSilence) {
                    flushRetainRing(output)
                    wasDroppingSilence = false
                }
                consecutiveSilentFrames = 0
                copyFrameZeroAlloc(input, output, frameStart, frameEnd)
            }
        }
    }

    /**
     * Returns `true` when the current silent frame should be kept in SPEED_UP mode
     * (every Nth frame is retained to preserve a sense of pacing).
     */
    private fun shouldKeepFrameInSpeedUpMode(): Boolean {
        val silentFramesPastMinimum = (consecutiveSilentFrames - minSilenceFrames).coerceAtLeast(0)
        return silentFramesPastMinimum % SPEED_UP_KEEP_EVERY_NTH_FRAME == 0
    }

    /**
     * Zero-allocation frame copy using the pre-allocated [tempFrameBuf].
     *
     * Reads frame bytes from [input] at [start]..[end] and writes them to [output].
     * No objects are allocated on the GC heap.
     */
    private fun copyFrameZeroAlloc(
        input: ByteBuffer,
        output: ByteBuffer,
        start: Int,
        end: Int,
    ) {
        val savedPosition = input.position()
        input.position(start)
        val frameSize = end - start
        if (frameSize in 1..tempFrameBuf.size) {
            input.get(tempFrameBuf, 0, frameSize)
            output.put(tempFrameBuf, 0, frameSize)
        } else if (frameSize > 0) {
            // Fallback for unexpected frame sizes (should not happen with valid PCM)
            val slice = input.slice()
            slice.limit(frameSize)
            output.put(slice)
        }
        input.position(savedPosition)
    }

    /**
     * Writes a single frame's bytes into the pre-allocated retain ring buffer.
     *
     * The ring buffer keeps the most recent [retainCapacity] bytes of dropped silence,
     * overwriting the oldest data when full. This is the only buffering needed for the
     * retain-window feature — zero allocations in the hot path.
     */
    private fun writeToRetainRing(
        input: ByteBuffer,
        start: Int,
        end: Int,
    ) {
        val frameSize = end - start
        if (frameSize <= 0 || frameSize > retainCapacity) return

        val savedPosition = input.position()
        input.position(start)

        for (i in 0 until frameSize) {
            retainRing[retainHead] = input.get()
            retainHead = (retainHead + 1) % retainCapacity
        }
        retainBytesAvailable = minOf(retainBytesAvailable + frameSize, retainCapacity)

        input.position(savedPosition)
    }

    /**
     * Flushes the retain ring buffer into [output], preserving insertion order
     * (oldest byte first). After flushing the ring is reset for the next silence block.
     */
    private fun flushRetainRing(output: ByteBuffer) {
        if (retainBytesAvailable == 0) return

        val readStart =
            if (retainBytesAvailable < retainCapacity) {
                // Ring is not full — oldest byte is at index 0
                0
            } else {
                // Ring is full — oldest byte is at current head (it wraps)
                retainHead
            }

        for (i in 0 until retainBytesAvailable) {
            output.put(retainRing[(readStart + i) % retainCapacity])
        }

        // Subtract retained (restored) frames from the skipped metric.
        // These frames were counted when entering the drop path but are now
        // written back to the output, so they were NOT truly skipped.
        if (retainFrameBytes > 0) {
            totalSkippedFrames -= retainBytesAvailable / retainFrameBytes
        }

        // Reset ring state
        retainBytesAvailable = 0
        retainHead = 0
    }

    /**
     * Returns the total duration of audio that has been skipped, in milliseconds.
     *
     * This metric can be displayed in the player UI / audio settings screen.
     * Use [resetSkippedMetric] to zero the counter (e.g. when starting a new track).
     */
    public fun getSkippedDurationMs(): Long {
        val format = inputAudioFormat ?: return 0L
        if (format.sampleRate == 0) return 0L
        return totalSkippedFrames * 1000L / format.sampleRate
    }

    /**
     * Resets the cumulative skipped-duration counter.
     */
    public fun resetSkippedMetric() {
        totalSkippedFrames = 0L
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        inputBuffers.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
        consecutiveSilentFrames = 0
        wasDroppingSilence = false
        retainHead = 0
        retainBytesAvailable = 0
        totalSkippedFrames = 0L
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
        bytesPerFrame = 0
        minSilenceFrames = 0
        retainRing = ByteArray(0)
        retainCapacity = 0
        retainFrameBytes = 0
        tempFrameBuf = ByteArray(0)
    }

    private companion object {
        private const val PCM_16_BIT_BYTES = 2
        private const val SPEED_UP_KEEP_EVERY_NTH_FRAME = 2

        /** Default retain window: 65 ms of silence kept before speech resumes. */
        private const val DEFAULT_RETAIN_WINDOW_MS = 65

        /** Minimum retain window (speech may sound clipped below this). */
        private const val MIN_RETAIN_WINDOW_MS = 50

        /** Maximum retain window (larger values reduce the effectiveness of skip-silence). */
        private const val MAX_RETAIN_WINDOW_MS = 80

        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
