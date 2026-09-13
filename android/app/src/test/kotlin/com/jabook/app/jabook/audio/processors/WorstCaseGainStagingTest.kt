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

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Worst-case gain staging across the real [AudioProcessorFactory] chain with all
 * level-manipulating stages at maximum:
 * LoudnessNormalizer (RMS target 0.35, gain clamp 0.1..10) ->
 * VolumeBoostProcessor (+200% = 3x, own soft limiter at -0.3 dBFS) ->
 * AutoVolumeLeveler (LUFS target -16, soft limiter 0.95, slew 0.5 dB/s).
 *
 * External review claimed that independent per-stage limiters cannot guarantee
 * chain headroom, so transients may exceed full scale before the slow
 * AutoVolumeLeveler reacts. These tests feed challenging signals through real
 * processor instances chained in factory order and lock the invariant that
 * every output sample satisfies |x| <= 1.0. Measured peaks are embedded in
 * assertion messages so any failure reports the actual peak.
 */
@RunWith(RobolectricTestRunner::class)
class WorstCaseGainStagingTest {
    private val sampleRate = 44_100

    @Test
    fun `quiet sine with all gain stages at max does not exceed full scale`() {
        // RMS ~0.03 drives LoudnessNormalizer toward its 10x clamp; 3x boost then
        // pushes sine peaks past the boost limiter knee.
        val input = sine(rms = 0.03f, seconds = 3)
        val peak = runChain(input)
        println("WorstCaseGainStaging [quiet sine RMS 0.03]: measured peak=$peak")

        assertTrue("chain must process audio, peak=$peak looks like a dead drain", peak > 0.5f)
        assertTrue(
            "quiet sine (RMS 0.03, normalize+boost200+autolevel): measured peak=$peak exceeds 1.0",
            peak <= 1.0f + EPS,
        )
    }

    @Test
    fun `high crest transients with all gain stages at max do not exceed full scale`() {
        // Quiet sine (RMS ~0.03) + short 0.98-amplitude bursts: crest factor >> 1,
        // the slew-lag scenario where per-stage limiters must catch the peak.
        val input = quietSineWithTransients(seconds = 3)
        val peak = runChain(input)
        println("WorstCaseGainStaging [high-crest transients 0.98]: measured peak=$peak")

        assertTrue("chain must process audio, peak=$peak looks like a dead drain", peak > 0.5f)
        assertTrue(
            "high-crest transients (0.98 peaks on quiet sine): measured peak=$peak exceeds 1.0",
            peak <= 1.0f + EPS,
        )
    }

    @Test
    fun `full-scale noise with all gain stages at max does not exceed full scale`() {
        val input = fullScaleNoise(seconds = 3)
        val peak = runChain(input)
        println("WorstCaseGainStaging [full-scale noise]: measured peak=$peak")

        assertTrue("chain must process audio, peak=$peak looks like a dead drain", peak > 0.5f)
        assertTrue(
            "full-scale noise burst: measured peak=$peak exceeds 1.0",
            peak <= 1.0f + EPS,
        )
    }

    // --- Signal generators ---

    private fun sine(
        rms: Float,
        seconds: Int,
    ): ShortArray {
        val amplitude = rms * sqrt(2f)
        val count = sampleRate * seconds
        return ShortArray(count) { i ->
            (amplitude * sin(2.0 * PI * 440.0 * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun quietSineWithTransients(seconds: Int): ShortArray {
        val count = sampleRate * seconds
        val amplitude = 0.03f * sqrt(2f)
        val burstLength = 32
        val burstPeriod = 16_384 // ~371 ms between bursts keeps overall RMS low
        return ShortArray(count) { i ->
            if (i % burstPeriod < burstLength) {
                val polarity = if ((i / burstPeriod) % 2 == 0) 1f else -1f
                (0.98f * polarity * Short.MAX_VALUE).toInt().toShort()
            } else {
                (amplitude * sin(2.0 * PI * 440.0 * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }

    private fun fullScaleNoise(seconds: Int): ShortArray {
        val random = Random(seed = 42)
        val count = sampleRate * seconds
        return ShortArray(count) {
            ((random.nextFloat() * 2f - 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    // --- Chain runner ---

    /**
     * Feeds [pcm] through the real factory-built chain (normalize + boost200 +
     * autolevel) in chunks so adaptive state converges, and returns the maximum
     * absolute output sample in the normalized [-1, 1] domain.
     */
    private fun runChain(pcm: ShortArray): Float {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = true,
                volumeBoostLevel = VolumeBoostLevel.Boost200,
                autoVolumeLeveling = true,
            )
        val chain = AudioProcessorFactory.createProcessorChain(settings).processors
        assertTrue("expected the three level stages in chain", chain.size == 3)

        val format = AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT)
        chain.forEach { it.configure(format) }

        val input =
            ByteBuffer
                .allocateDirect(pcm.size * 2)
                .order(ByteOrder.nativeOrder())
                .apply {
                    pcm.forEach { putShort(it) }
                    flip()
                }

        val drained = ByteArrayOutputStream()
        val chunkBytes = 2048 // 1024 frames per chunk, ~23 ms at 44.1 kHz
        while (input.hasRemaining()) {
            val chunk = input.duplicate()
            chunk.limit(minOf(chunk.limit(), chunk.position() + chunkBytes))
            input.position(chunk.limit())
            drained.write(feedThroughChain(chain, chunk))
        }

        val output = ByteBuffer.wrap(drained.toByteArray()).order(ByteOrder.nativeOrder())
        var peak = 0f
        while (output.hasRemaining()) {
            val normalized = abs(output.short.toInt()) / Short.MAX_VALUE.toFloat()
            if (normalized > peak) peak = normalized
        }
        return peak
    }

    /** Queues [chunk] into each active processor in order, draining outputs between stages. */
    private fun feedThroughChain(
        chain: List<AudioProcessor>,
        chunk: ByteBuffer,
    ): ByteArray {
        var buffer: ByteBuffer = chunk
        for (processor in chain) {
            // Inactive processors discard queueInput bytes; route around them.
            if (!processor.isActive) continue
            processor.queueInput(buffer)
            val stageOutput = ByteArrayOutputStream()
            var out = processor.getOutput()
            while (out.hasRemaining()) {
                val bytes = ByteArray(out.remaining())
                out.get(bytes)
                stageOutput.write(bytes)
                out = processor.getOutput()
            }
            buffer = ByteBuffer.wrap(stageOutput.toByteArray()).order(ByteOrder.nativeOrder())
        }
        return buffer.array()
    }

    private companion object {
        /** A couple of PCM16 quantization steps of tolerance. */
        const val EPS = 1e-4f
    }
}
