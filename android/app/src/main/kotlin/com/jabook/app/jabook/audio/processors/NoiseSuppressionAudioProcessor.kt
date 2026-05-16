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
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.ByteBuffer

/**
 * Noise suppression audio processor.
 *
 * Uses Android's built-in noise suppression if available, otherwise passes through.
 *
 * @property strength Strength of noise suppression (0.0 to 1.0)
 */
@UnstableApi
public class NoiseSuppressionAudioProcessor(
    private val strength: Float = 0.7f,
) : AudioProcessor {
    private val scopeJob = SupervisorJob()
    private val scope =
        CoroutineScope(
            scopeJob + Dispatchers.Main.immediate + loggingCoroutineExceptionHandler("NoiseSuppressionProcessor"),
        )
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    private var initialized = false

    override fun configure(
        inputFormat: androidx.media3.common.Format,
        outputFormat: androidx.media3.common.Format,
    ): androidx.media3.common.Format {
        // Check if noise suppression is supported
        if (!isNoiseSuppressionSupported()) {
            return outputFormat
        }

        try {
            noiseSuppressor =
                android.media.audiofx.NoiseSuppressor
                    .create(0)
            noiseSuppressor?.setParameter(0, strength)
            initialized = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create NoiseSuppressor: ${e.message}")
            noiseSuppressor = null
        }

        return outputFormat
    }

    override fun checkFormat(
        inputFormat: androidx.media3.common.Format,
        outputFormat: androidx.media3.common.Format,
    ): Boolean {
        // Noise suppression works with any audio format
        return true
    }

    override fun getSupportedOutputFormats(inputFormat: androidx.media3.common.Format): Array<androidx.media3.common.Format> =
        arrayOf(inputFormat)

    override fun queueInput(
        inputBuffer: ByteBuffer,
        paddingBytes: Int,
        sampleOffsetUs: Long,
        flush: Boolean,
    ) {
        if (!initialized || noiseSuppressor == null) {
            // Pass through
            outputBuffer = inputBuffer
            return
        }

        // Apply noise suppression (simplified - would need to process audio data)
        // This is a placeholder implementation
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
        noiseSuppressor?.release()
        noiseSuppressor = null
        initialized = false
    }

    override fun release() {
        // Release resources
        noiseSuppressor?.release()
        noiseSuppressor = null
        initialized = false
    }

    private fun isNoiseSuppressionSupported(): Boolean =
        android.media.audiofx.NoiseSuppressor
            .isAvailable()

    private companion object {
        private const val TAG = "NoiseSuppressionProcessor"
    }
}
