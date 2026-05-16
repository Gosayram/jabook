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

package com.jabook.app.jabook.audio

import android.content.Context
import androidx.media3.common.Metadata
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class PlayerMetadataHandlerTest {
    private val handler =
        PlayerMetadataHandler(
            context = mock(Context::class.java),
            setEmbeddedArtworkPath = {},
        )

    @Test
    fun `onMetadata applies track gain when both track and album tags exist`() {
        val normalizer = mock(LoudnessNormalizer::class.java)
        handler.loudnessNormalizer = normalizer
        val metadata =
            mock(Metadata::class.java).also { mocked ->
                whenever(mocked.length()).thenReturn(2)
                whenever(mocked.get(0)).thenReturn(mockMetadataEntry("REPLAYGAIN_ALBUM_GAIN: -4.0 dB"))
                whenever(mocked.get(1)).thenReturn(mockMetadataEntry("REPLAYGAIN_TRACK_GAIN: -6.5 dB"))
            }

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(-6.5f)
    }

    @Test
    fun `onMetadata does not call normalizer when replay gain is invalid`() {
        val normalizer = mock(LoudnessNormalizer::class.java)
        handler.loudnessNormalizer = normalizer
        val metadata =
            mock(Metadata::class.java).also { mocked ->
                whenever(mocked.length()).thenReturn(1)
                whenever(mocked.get(0)).thenReturn(mockMetadataEntry("REPLAYGAIN_TRACK_GAIN: not_a_number"))
            }

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(org.mockito.kotlin.any())
    }

    private fun mockMetadataEntry(value: String): Metadata.Entry {
        val entry = mock(Metadata.Entry::class.java)
        whenever(entry.toString()).thenReturn(value)
        return entry
    }
}
