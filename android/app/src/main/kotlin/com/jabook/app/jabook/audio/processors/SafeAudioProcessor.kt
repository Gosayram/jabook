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

/**
 * Wraps an [AudioProcessor] with graceful degradation: if the delegate throws
 * during [queueInput] or [configure], this wrapper falls back to passthrough
 * mode instead of crashing the entire audio pipeline.
 *
 * P-67: Without this, a failing [LoudnessNormalizer] or [SpeechEnhancer] causes
 * ExoPlayer to report an [AudioSink] error and stop playback entirely. With this
 * wrapper, playback continues with unprocessed audio — a much better UX.
 *
 * Usage:
 * ```
 * val safe = SafeAudioProcessor(LoudnessNormalizer(settings)) { error ->
 *     analytics.reportProcessorError("LoudnessNormalizer", error)
 * }
 * ```
 *
 * @param delegate The real AudioProcessor to wrap
 * @param onError Callback invoked when the delegate fails (for analytics/logging)
 */
@UnstableApi
public class SafeAudioProcessor(
    private val delegate: AudioProcessor,
    private val onError: (Throwable) -> Unit = {},
) : AudioProcessor {
    private var hasFailed = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (hasFailed) return inputAudioFormat
        return try {
            delegate.configure(inputAudioFormat)
        } catch (e: Throwable) {
            degrade("configure", e)
            inputAudioFormat
        }
    }

    override fun isActive(): Boolean = !hasFailed && delegate.isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (hasFailed) {
            outputBuffer = inputBuffer
            return
        }
        try {
            delegate.queueInput(inputBuffer)
        } catch (e: Throwable) {
            degrade("queueInput", e)
            outputBuffer = inputBuffer
        }
    }

    override fun queueEndOfStream() {
        if (!hasFailed) {
            try {
                delegate.queueEndOfStream()
            } catch (e: Throwable) {
                degrade("queueEndOfStream", e)
            }
        }
    }

    override fun getOutput(): ByteBuffer {
        if (hasFailed) {
            val buffer = outputBuffer
            outputBuffer = EMPTY_BUFFER
            return buffer
        }
        return try {
            delegate.output
        } catch (e: Throwable) {
            degrade("getOutput", e)
            EMPTY_BUFFER
        }
    }

    override fun isEnded(): Boolean {
        if (hasFailed) return true
        return try {
            delegate.isEnded
        } catch (e: Throwable) {
            degrade("isEnded", e)
            true
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun flush() {
        if (!hasFailed) {
            try {
                delegate.flush()
            } catch (e: Throwable) {
                degrade("flush", e)
            }
        }
        outputBuffer = EMPTY_BUFFER
    }

    override fun reset() {
        if (!hasFailed) {
            try {
                delegate.reset()
            } catch (e: Throwable) {
                degrade("reset", e)
            }
        }
        outputBuffer = EMPTY_BUFFER
    }

    /**
     * Whether this processor has degraded to passthrough mode.
     */
    public fun isDegraded(): Boolean = hasFailed

    /**
     * Resets the degraded state, allowing the delegate to be retried.
     */
    public fun recover() {
        hasFailed = false
        outputBuffer = EMPTY_BUFFER
        LogUtils.i(TAG, "Recovered from degraded state for ${delegate.javaClass.simpleName}")
    }

    private fun degrade(
        method: String,
        error: Throwable,
    ) {
        if (hasFailed) return
        hasFailed = true
        val name = delegate.javaClass.simpleName
        LogUtils.e(TAG, "AudioProcessor $name failed in $method — degrading to passthrough", error)
        onError(error)
    }

    private companion object {
        private const val TAG = "SafeAudioProc"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
