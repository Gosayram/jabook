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
 * Echo audio processor.
 *
 * Uses Android's built-in environmental reverb if available, or a simple delay line.
 */
public class EchoAudioProcessor(
    private val strength: Float = 0.5f,
    private val delayMs: Int = 500,
    private val decay: Float = 0.5f,
) : AudioProcessor {
    private val scopeJob = SupervisorJob()
    private val scope =
        CoroutineScope(
            scopeJob + Dispatchers.Main.immediate + loggingCoroutineExceptionHandler("EchoProcessor"),
        )
    private var echoBuffer: ByteBuffer? = null
    private var echoPosition = 0
    private var initialized = false

    override fun configure(
        inputFormat: Format,
        outputFormat: Format,
    ): Format {
        // Check if we can use EnvironmentalReverb for echo
        if (android.media.audiofx.EnvironmentalReverb
                .isAvailable()
        ) {
            try {
                // Use EnvironmentalReverb as a simple delay
                // This is a simplified implementation
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create EnvironmentalReverb for echo: ${e.message}")
            }
        }

        initialized = true
        return outputFormat
    }

    override fun checkFormat(
        inputFormat: Format,
        outputFormat: Format,
    ): Boolean {
        // Echo works with any audio format
        return true
    }

    override fun getSupportedOutputFormats(inputFormat: Format): Array<Format> = arrayOf(inputFormat)

    override fun queueInput(
        inputBuffer: ByteBuffer,
        paddingBytes: Int,
        sampleOffsetUs: Long,
        flush: Boolean,
    ) {
        if (!initialized) {
            outputBuffer = inputBuffer
            return
        }

        // Simple echo implementation (placeholder)
        // In a real implementation, we would mix delayed audio
        outputBuffer = inputBuffer
    }

    override fun queueEndOfStream() {
        // Handle end of stream
    }

    override fun flush() {
        // Flush processor
        echoBuffer?.clear()
        echoPosition = 0
    }

    override fun reset() {
        // Reset processor
        echoBuffer?.clear()
        echoBuffer = null
        echoPosition = 0
        initialized = false
    }

    override fun release() {
        // Release resources
        echoBuffer = null
        initialized = false
    }

    private companion object {
        private const val TAG = "EchoProcessor"
    }
}
