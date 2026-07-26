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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeAudioProcessorTest {
    private lateinit var errors: MutableList<Throwable>

    @Before
    fun setUp() {
        errors = mutableListOf()
    }

    private fun createSafe(delegate: androidx.media3.common.audio.AudioProcessor): SafeAudioProcessor =
        SafeAudioProcessor(delegate) { errors.add(it) }

    // --- configure failure degrades to passthrough ---

    @Test
    fun `configure failure degrades to passthrough`() {
        val failing = ThrowingProcessor(throwInConfigure = true)
        val safe = createSafe(failing)

        val inputFormat = createAudioFormat()
        val result = safe.configure(inputFormat)

        assertEquals(inputFormat.sampleRate, result.sampleRate)
        assertTrue(safe.isDegraded())
        assertEquals(1, errors.size)
    }

    // --- queueInput failure degrades ---

    @Test
    fun `queueInput failure degrades to passthrough`() {
        val failing = ThrowingProcessor(throwInQueueInput = true)
        val safe = createSafe(failing)
        safe.configure(createAudioFormat())

        val buffer = java.nio.ByteBuffer.allocate(1024)
        safe.queueInput(buffer)

        assertTrue(safe.isDegraded())
    }

    // --- healthy processor passes through ---

    @Test
    fun `healthy processor works normally`() {
        val healthy = HealthyProcessor()
        val safe = createSafe(healthy)

        safe.configure(createAudioFormat())
        assertFalse(safe.isDegraded())
        assertFalse(safe.isActive)
    }

    // --- only first error triggers callback ---

    @Test
    fun `only first error triggers onError callback`() {
        val failing = ThrowingProcessor(throwInConfigure = true, throwInQueueInput = true)
        val safe = createSafe(failing)

        safe.configure(createAudioFormat())

        val buffer = java.nio.ByteBuffer.allocate(1024)
        safe.queueInput(buffer)

        assertEquals(1, errors.size)
    }

    // --- recover resets degraded state ---

    @Test
    fun `recover resets degraded state`() {
        val failing = ThrowingProcessor(throwInConfigure = true)
        val safe = createSafe(failing)

        safe.configure(createAudioFormat())
        assertTrue(safe.isDegraded())

        safe.recover()
        assertFalse(safe.isDegraded())
    }

    private fun createAudioFormat(): androidx.media3.common.audio.AudioProcessor.AudioFormat =
        androidx.media3.common.audio.AudioProcessor.AudioFormat(
            // sampleRate=
            44100,
            // channelCount=
            2,
            // encoding=
            2,
        )

    private class ThrowingProcessor(
        private val throwInConfigure: Boolean = false,
        private val throwInQueueInput: Boolean = false,
    ) : androidx.media3.common.audio.AudioProcessor {
        override fun configure(
            inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat,
        ): androidx.media3.common.audio.AudioProcessor.AudioFormat {
            if (throwInConfigure) throw RuntimeException("configure failed")
            return inputAudioFormat
        }

        override fun isActive(): Boolean = false

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (throwInQueueInput) throw RuntimeException("queueInput failed")
        }

        override fun queueEndOfStream() = Unit

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = true

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun flush() = Unit

        override fun reset() = Unit
    }

    private class HealthyProcessor : androidx.media3.common.audio.AudioProcessor {
        override fun configure(
            inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat,
        ): androidx.media3.common.audio.AudioProcessor.AudioFormat = inputAudioFormat

        override fun isActive(): Boolean = false

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) = Unit

        override fun queueEndOfStream() = Unit

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = true

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun flush() = Unit

        override fun reset() = Unit
    }
}
