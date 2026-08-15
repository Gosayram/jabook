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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VolumeBoostProcessorTest {
    @Test
    fun `boost one hundred doubles quiet samples below limiter knee`() {
        val processor = configureProcessor(VolumeBoostLevel.Boost100)

        processor.queueInput(constPcmBuffer((0.2f * Short.MAX_VALUE).toInt(), 4))

        val output = processor.getOutput().order(ByteOrder.nativeOrder())
        val ratio = output.short.toInt().toFloat() / (0.2f * Short.MAX_VALUE)
        assertTrue(
            "ratio=$ratio should be ~2.0 for Boost100 below the limiter knee",
            ratio in 1.95f..2.05f,
        )
    }

    @Test
    fun `off level stays inactive`() {
        val processor = configureProcessor(VolumeBoostLevel.Off)

        assertFalse(processor.isActive())
    }

    @Test
    fun `dead look-ahead state is removed`() {
        val fieldNames = VolumeBoostProcessor::class.java.declaredFields.map { it.name }
        assertTrue(
            "No look-ahead fields should remain: $fieldNames",
            fieldNames.none { it.contains("lookahead", ignoreCase = true) },
        )
    }

    private fun configureProcessor(level: VolumeBoostLevel): VolumeBoostProcessor {
        val processor = VolumeBoostProcessor(level)
        processor.configure(
            AudioProcessor.AudioFormat(
                44_100,
                1,
                C.ENCODING_PCM_16BIT,
            ),
        )
        return processor
    }

    private fun constPcmBuffer(
        amplitude: Int,
        sampleCount: Int,
    ): ByteBuffer =
        ByteBuffer
            .allocateDirect(sampleCount * 2)
            .order(ByteOrder.nativeOrder())
            .apply {
                repeat(sampleCount) { putShort(amplitude.toShort()) }
                flip()
            }
}
