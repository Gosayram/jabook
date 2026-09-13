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
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Chain-head PCM converter: float PCM in → 16-bit PCM out (sample-rate/channel preserving).
 *
 * jabook's DSP processors ([LoudnessNormalizer], [SpeechEnhancer], …) are strictly
 * 16-bit: they deactivate on any other input encoding, and a pipeline configured for
 * float input rejects the whole stream with `UnhandledAudioFormatException` (surfaced
 * by ExoPlayer as renderer error 5001 "Unhandled input format"). This processor sits
 * FIRST in the chain assembled by [AudioProcessorFactory.createProcessorChain] so
 * 16-bit-only DSP always sees int16.
 *
 * - 16-bit input → returns [AudioFormat.NOT_SET] → processor stays inactive (zero copy).
 * - float input → converts to int16 with sample-count preservation.
 * - anything else → [UnhandledAudioFormatException]; the service falls back to a
 *   processor-free player for such exotic formats.
 */
@UnstableApi
public class FloatToInt16PcmProcessor : BaseAudioProcessor() {
    /** Trailing bytes of a frame split across [queueInput] calls (0–3 bytes). */
    private var carry: ByteArray = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> AudioProcessor.AudioFormat.NOT_SET
            C.ENCODING_PCM_FLOAT ->
                AudioProcessor.AudioFormat(
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount,
                    C.ENCODING_PCM_16BIT,
                )
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (carry.isEmpty() && remaining % FRAME_SIZE_BYTES == 0) {
            // Fast path: frame-aligned buffer with no split-frame carry-over.
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            emitFrames(inputBuffer, remaining / FRAME_SIZE_BYTES)
            return
        }

        // Slow path: a frame straddles two queueInput calls — buffer via carry.
        val combined = ByteArray(carry.size + remaining)
        carry.copyInto(combined)
        inputBuffer.get(combined, carry.size, remaining)
        val frames = combined.size / FRAME_SIZE_BYTES
        val consumed = frames * FRAME_SIZE_BYTES
        carry =
            if (consumed < combined.size) {
                combined.copyOfRange(consumed, combined.size)
            } else {
                ByteArray(0)
            }
        if (frames == 0) {
            replaceOutputBuffer(0)
            return
        }
        emitFrames(
            ByteBuffer
                .wrap(combined, 0, consumed)
                .order(ByteOrder.LITTLE_ENDIAN),
            frames,
        )
    }

    override fun onQueueEndOfStream() {
        // A trailing sub-frame (< 4 bytes) cannot be represented in int16 — drop it.
        carry = ByteArray(0)
    }

    private fun emitFrames(
        input: ByteBuffer,
        frames: Int,
    ) {
        val output = replaceOutputBuffer(frames * BYTES_PER_SHORT)
        repeat(frames) {
            val sample = input.float.coerceIn(-1.0f, 1.0f)
            output.putShort((sample * MAX_INT16).roundToInt().toShort())
        }
    }

    private companion object {
        /** 32-bit float sample = 4 bytes. */
        const val FRAME_SIZE_BYTES = 4

        const val BYTES_PER_SHORT = 2

        const val MAX_INT16 = 32767f
    }
}
