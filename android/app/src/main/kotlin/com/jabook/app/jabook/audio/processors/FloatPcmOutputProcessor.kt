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
import java.nio.ByteBuffer

/**
 * Tail [AudioProcessor] that negotiates 32-bit float output for the whole
 * [androidx.media3.common.audio.AudioProcessorChain].
 *
 * Media3 1.11.0's [androidx.media3.exoplayer.audio.DefaultAudioSink] configures its
 * AudioTrack with whatever PCM encoding the processing pipeline's final
 * [AudioProcessor.AudioFormat] declares. Returning `ENCODING_PCM_FLOAT` from
 * [onConfigure] therefore makes the sink output float PCM — the same negotiation
 * pattern Gramophone uses in its ReplayGainAudioProcessor.
 *
 * Placement matters: jabook's DSP processors are strictly 16-bit (they reject any
 * other input encoding), so this processor must be appended AFTER them, converting
 * the chain's int16 output to float. It must NOT be prepended.
 *
 * Sink-level `setEnableFloatOutput` must stay disabled when processors are attached:
 * in Media3 1.11.0 the sink drops the entire processor chain from the pipeline when
 * float output is enabled and the input is hi-res/float PCM.
 */
public class FloatPcmOutputProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // The sink guarantees int16 input here (ToInt16PcmAudioProcessor precedes
            // the chain); anything else is a wiring bug, not a runtime condition.
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_FLOAT,
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameBytes = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(frameBytes * 2)
        // Same math as Media3's ToFloatPcmAudioProcessor: short << 16 scaled by
        // 1/0x7FFFFFFF, i.e. value in [-1.0, 1.0).
        while (inputBuffer.hasRemaining()) {
            val pcm16 = inputBuffer.short
            val pcm32 = pcm16.toInt() shl 16
            outputBuffer.putFloat((pcm32 * PCM_32BIT_INT_TO_FLOAT_FACTOR).toFloat())
        }
    }

    private companion object {
        const val PCM_32BIT_INT_TO_FLOAT_FACTOR = 1.0 / 0x7FFFFFFF
    }
}
