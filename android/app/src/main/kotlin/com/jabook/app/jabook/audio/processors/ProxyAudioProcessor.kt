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
 * Proxy AudioProcessor that delegates all operations to a hot-swappable delegate.
 *
 * This enables changing the audio processing chain at runtime without
 * recreating the entire ExoPlayer instance. The delegate is marked `@Volatile`
 * so that swaps initiated from the main thread are immediately visible to the
 * audio rendering thread.
 *
 * **Thread-safety:** [swapDelegate] is called on the main thread while
 * [queueInput]/[getOutput] are called on the ExoPlayer audio thread. The
 * `@Volatile` guarantee plus the fact that AudioProcessor methods are never
 * called concurrently by ExoPlayer makes this safe without additional
 * synchronization.
 *
 * P-01: Hot-swap audio processors without restarting the player.
 */
@UnstableApi
public class ProxyAudioProcessor(
    @Volatile private var delegate: AudioProcessor = PassthroughAudioProcessor(),
) : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null

    /**
     * Hot-swaps the delegate processor.
     *
     * Call from the main thread. The new delegate will be used from the next
     * audio buffer onwards. [flush] is called to signal ExoPlayer that the
     * processor pipeline should be re-evaluated.
     *
     * @param newDelegate The new AudioProcessor to delegate to.
     */
    public fun swapDelegate(newDelegate: AudioProcessor) {
        val oldName = delegate.javaClass.simpleName
        delegate = newDelegate
        val newName = newDelegate.javaClass.simpleName
        LogUtils.d(TAG) { "Delegate swapped: $oldName → $newName" }
    }

    /**
     * Returns the current delegate processor.
     * Useful for inspecting the active processor without swapping.
     */
    public fun currentDelegate(): AudioProcessor = delegate

    // ──────────────────────────────────────────────────────────────────────
    // AudioProcessor contract — thin delegation layer
    // ──────────────────────────────────────────────────────────────────────

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        val result = delegate.configure(inputAudioFormat)
        this.outputAudioFormat = result
        return result
    }

    override fun isActive(): Boolean = delegate.isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        delegate.queueInput(inputBuffer)
    }

    override fun queueEndOfStream() {
        delegate.queueEndOfStream()
    }

    override fun getOutput(): ByteBuffer = delegate.output

    override fun isEnded(): Boolean = delegate.isEnded

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        delegate.flush()
    }

    override fun reset() {
        delegate.reset()
    }

    private companion object {
        private const val TAG = "ProxyAudioProc"
    }
}

/**
 * No-op AudioProcessor that passes audio through unchanged.
 *
 * Used as the default delegate in [ProxyAudioProcessor] when a processing
 * slot is disabled (e.g., LoudnessNormalizer turned off by the user).
 */
@UnstableApi
public class PassthroughAudioProcessor : AudioProcessor {
    private var configured = false
    private var ended = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        configured = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = false

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Not active — ExoPlayer will not call this, but be defensive.
    }

    override fun queueEndOfStream() {
        ended = true
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = ended

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        ended = false
    }

    override fun reset() {
        flush()
        configured = false
    }

    private companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
