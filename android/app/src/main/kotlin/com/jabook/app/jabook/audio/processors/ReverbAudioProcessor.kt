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

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.ByteBuffer

/**
 * Reverb audio processor.
 *
 * Uses Android's built-in environmental reverb if available.
 */
public class ReverbAudioProcessor(
    private val strength: Float = 0.5f,
) : AudioProcessor {
    private val scopeJob = SupervisorJob()
    private val scope =
        CoroutineScope(
            scopeJob + Dispatchers.Main.immediate + loggingCoroutineExceptionHandler("ReverbProcessor"),
        )
    private var reverb: android.media.audiofx.EnvironmentalReverb? = null
    private var initialized = false

    override fun configure(
        inputFormat: Format,
        outputFormat: Format,
    ): Format {
        if (!isReverbSupported()) {
            return outputFormat
        }

        try {
            reverb = android.media.audiofx.EnvironmentalReverb(0, android.media.audiofx.EnvironmentalReverb.PRESET_NONE)
            // Set reverb parameters based on strength
            reverb?.setDecayTime(500f + strength * 1000f)
            reverb?.setRoomSize(0.5f + strength * 0.5f)
            initialized = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create EnvironmentalReverb: ${e.message}")
            reverb = null
        }

        return outputFormat
    }

    override fun checkFormat(
        inputFormat: Format,
        outputFormat: Format,
    ): Boolean {
        // Reverb works with any audio format
        return true
    }

    override fun getSupportedOutputFormats(inputFormat: Format): Array<Format> = arrayOf(inputFormat)

    override fun queueInput(
        inputBuffer: ByteBuffer,
        paddingBytes: Int,
        sampleOffsetUs: Long,
        flush: Boolean,
    ) {
        if (!initialized || reverb == null) {
            outputBuffer = inputBuffer
            return
        }

        // Apply reverb (placeholder - would need to process audio data)
        outputBuffer = inputBuffer
    }

    override fun queueEndOfStream() {
        // Handle end of stream
    }

    override fun flush() {
        // Flush processor
    }

    override fun reset() {
        // Reset processor
        reverb?.release()
        reverb = null
        initialized = false
    }

    override fun release() {
        // Release resources
        reverb?.release()
        reverb = null
        initialized = false
    }

    private fun isReverbSupported(): Boolean =
        android.media.audiofx.EnvironmentalReverb
            .isAvailable()

    private companion object {
        private const val TAG = "ReverbProcessor"
    }
}
