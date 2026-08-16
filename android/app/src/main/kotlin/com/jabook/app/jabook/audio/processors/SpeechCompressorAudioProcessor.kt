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
import com.jabook.app.jabook.util.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * 3-band speech compressor audio processor.
 *
 * Divides audio into low (0-300Hz), mid (300-2000Hz), and high (2000Hz+)
 * bands using 2nd-order Butterworth crossover filters. Each band is compressed
 * independently (RATIO 4:1, attack 10ms, release 100ms) with automatic makeup
 * gain set to 50% of gain reduction.
 *
 * Intensity levels:
 * - Gentle: threshold -15 dB
 * - Moderate: threshold -20 dB
 * - Aggressive: threshold -25 dB
 */
@UnstableApi
public class SpeechCompressorAudioProcessor(
    private val level: SpeechCompressorLevel,
) : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Crossover filter coefficients (2nd-order Butterworth biquad)
    private data class BiquadCoeffs(
        var b0: Float = 0f,
        var b1: Float = 0f,
        var b2: Float = 0f,
        var a1: Float = 0f,
        var a2: Float = 0f,
    )

    private data class BiquadState(
        var x1: Float = 0f,
        var x2: Float = 0f,
        var y1: Float = 0f,
        var y2: Float = 0f,
    )

    private var lpCoeffs = BiquadCoeffs()
    private var hpCoeffs = BiquadCoeffs()
    private var lpStates: Array<BiquadState> = emptyArray()
    private var hpStates: Array<BiquadState> = emptyArray()

    // Per-band envelope followers (index = channel * 3 + band)
    private var envelopes = FloatArray(0)

    // Compression parameters
    private var thresholdDb = -20f
    private var attackCoeff = 0f
    private var releaseCoeff = 0f

    // Input/output buffer management
    private var queuedInputBuffer: ByteBuffer? = null
    private var queuedInputCapacity: Int = 0
    private var queuedInputBytes = 0
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    init {
        when (level) {
            SpeechCompressorLevel.Off -> isActive = false
            SpeechCompressorLevel.Gentle -> thresholdDb = -15f
            SpeechCompressorLevel.Moderate -> thresholdDb = -20f
            SpeechCompressorLevel.Aggressive -> thresholdDb = -25f
        }
        LogUtils.d(TAG, "Initialized: level=$level threshold=${thresholdDb}dB")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        isActive = level != SpeechCompressorLevel.Off &&
            inputAudioFormat.encoding == android.media.AudioFormat.ENCODING_PCM_16BIT

        if (isActive) {
            val sampleRate = inputAudioFormat.sampleRate.toFloat()
            val channels = inputAudioFormat.channelCount

            // Pre-compute crossover filter coefficients
            lpCoeffs = computeBiquadCoeffs(sampleRate, CROSSOVER_LP_HZ, isLowPass = true)
            hpCoeffs = computeBiquadCoeffs(sampleRate, CROSSOVER_HP_HZ, isLowPass = false)

            // Initialize per-channel filter states
            lpStates = Array(channels) { BiquadState() }
            hpStates = Array(channels) { BiquadState() }

            // Envelope follower coefficients from attack/release times
            val attackSec = ATTACK_MS / 1000f
            val releaseSec = RELEASE_MS / 1000f
            attackCoeff = 1f - exp(-1f / (attackSec * sampleRate))
            releaseCoeff = 1f - exp(-1f / (releaseSec * sampleRate))

            // Initialize per-band envelope followers
            envelopes = FloatArray(channels * 3)
        }

        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false

        LogUtils.d(
            TAG,
            "Configured: sampleRate=${inputAudioFormat.sampleRate} " +
                "channels=${inputAudioFormat.channelCount} active=$isActive " +
                "threshold=${thresholdDb}dB",
        )
        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) return
        if (inputBuffer.hasRemaining()) {
            val remaining = inputBuffer.remaining()
            ensureQueuedInputCapacity(remaining)
            queuedInputBytes += remaining
            queuedInputBuffer!!.put(inputBuffer)
        }
    }

    private fun ensureQueuedInputCapacity(additionalBytes: Int) {
        val required = queuedInputBytes + additionalBytes
        if (required <= queuedInputCapacity) return
        val newCapacity = maxOf(required, queuedInputCapacity * 2, 4096)
        val newBuffer = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
        queuedInputBuffer?.let { old ->
            old.flip()
            newBuffer.put(old)
        }
        queuedInputBuffer = newBuffer
        queuedInputCapacity = newCapacity
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        if (outputBuffer?.hasRemaining() == true) return outputBuffer!!
        if (!isActive || queuedInputBytes == 0) return EMPTY_BUFFER

        val totalSize = queuedInputBytes

        val preparedOutputBuffer =
            if (outputBuffer == null || outputBuffer!!.capacity() < totalSize) {
                ByteBuffer.allocateDirect(totalSize).order(ByteOrder.nativeOrder()).also {
                    outputBuffer = it
                }
            } else {
                outputBuffer!!.clear()
                outputBuffer
            } ?: return EMPTY_BUFFER

        queuedInputBuffer?.let { buf ->
            buf.flip()
            processBuffer(buf, preparedOutputBuffer)
        }
        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        preparedOutputBuffer.flip()
        return preparedOutputBuffer
    }

    override fun isEnded(): Boolean = inputEnded && queuedInputBytes == 0

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun flush() {
        for (state in lpStates) {
            state.x1 = 0f
            state.x2 = 0f
            state.y1 = 0f
            state.y2 = 0f
        }
        for (state in hpStates) {
            state.x1 = 0f
            state.x2 = 0f
            state.y1 = 0f
            state.y2 = 0f
        }
        envelopes.fill(0f)
        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        lpStates = emptyArray()
        hpStates = emptyArray()
        envelopes = FloatArray(0)
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
    }

    private fun processBuffer(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val format = inputAudioFormat ?: return
        if (format.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) {
            output.put(input)
            return
        }
        val channels = format.channelCount
        val samples = input.remaining() / (2 * channels)
        applySpeechCompression(input, output, samples, channels)
    }

    /**
     * 3-band speech compression: crossover filters → per-band compression → sum → output.
     */
    private fun applySpeechCompression(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
    ) {
        val invMaxValue = 1f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()
        val minLog = 1e-10f

        for (i in 0 until samples) {
            for (ch in 0 until channels) {
                val rawSample = input.short
                val normalized = rawSample * invMaxValue

                // Apply crossover filters to get 3 bands
                val lowBand = processBiquad(normalized, lpStates[ch], lpCoeffs)
                val highBand = processBiquad(normalized, hpStates[ch], hpCoeffs)
                val midBand = normalized - lowBand - highBand

                // Compress each band independently and sum
                var sum = 0f
                for (band in 0 until 3) {
                    val bandSample =
                        when (band) {
                            0 -> lowBand
                            1 -> midBand
                            else -> highBand
                        }
                    val absSample = if (bandSample >= 0) bandSample else -bandSample

                    // Envelope follower (RMS-style with separate attack/release)
                    val envIdx = ch * 3 + band
                    val envelope = envelopes[envIdx]
                    val updatedEnvelope =
                        if (absSample > envelope) {
                            envelope + attackCoeff * (absSample - envelope)
                        } else {
                            envelope + releaseCoeff * (absSample - envelope)
                        }
                    envelopes[envIdx] = updatedEnvelope

                    // Gain computer
                    val db = 20f * log10(updatedEnvelope + minLog)
                    val gainReductionDb =
                        if (db > thresholdDb) {
                            (db - thresholdDb) * (RATIO - 1f) / RATIO
                        } else {
                            0f
                        }

                    // Apply compression with 50% makeup gain
                    val totalGainDb = -gainReductionDb * 0.5f
                    val totalGain = 10f.pow(totalGainDb / 20f)
                    sum += bandSample * totalGain
                }

                // Clamp and output
                val clamped = sum.coerceIn(-1f, 1f)
                output.putShort((clamped * maxValue).toInt().toShort())
            }
        }
    }

    /**
     * Computes biquad coefficients for a 2nd-order Butterworth filter.
     */
    private fun computeBiquadCoeffs(
        sampleRate: Float,
        cutoffHz: Float,
        isLowPass: Boolean,
    ): BiquadCoeffs {
        val k = tan(kotlin.math.PI.toFloat() * cutoffHz / sampleRate)
        val k2 = k * k
        val norm = 1f / (1f + SQRT2 * k + k2)
        val a1 = 2f * (k2 - 1f) * norm
        val a2 = (1f - SQRT2 * k + k2) * norm

        return if (isLowPass) {
            BiquadCoeffs(
                b0 = k2 * norm,
                b1 = 2f * k2 * norm,
                b2 = k2 * norm,
                a1 = a1,
                a2 = a2,
            )
        } else {
            BiquadCoeffs(
                b0 = 1f * norm,
                b1 = -2f * norm,
                b2 = 1f * norm,
                a1 = a1,
                a2 = a2,
            )
        }
    }

    /**
     * Processes one sample through a biquad filter, updating state in place.
     */
    private fun processBiquad(
        input: Float,
        state: BiquadState,
        coeffs: BiquadCoeffs,
    ): Float {
        val output =
            coeffs.b0 * input +
                coeffs.b1 * state.x1 +
                coeffs.b2 * state.x2 -
                coeffs.a1 * state.y1 -
                coeffs.a2 * state.y2
        state.x2 = state.x1
        state.x1 = input
        state.y2 = state.y1
        state.y1 = output
        return output
    }

    public companion object {
        private const val TAG = "SpeechCompressorAP"
        private const val SQRT2 = 1.4142135623730951f
        private const val CROSSOVER_LP_HZ = 300f
        private const val CROSSOVER_HP_HZ = 2000f
        private const val RATIO = 4f
        private const val ATTACK_MS = 10f
        private const val RELEASE_MS = 100f
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
