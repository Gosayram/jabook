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

import androidx.media3.common.Metadata
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class PlayerMetadataHandlerTest {
    private val handler =
        PlayerMetadataHandler(
            context = mock(),
            setEmbeddedArtworkPath = {},
        )

    @Test
    fun `onMetadata applies track gain when both track and album tags exist`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        // Use a real metadata with actual entry instead of mocking toString()
        val trackGainEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-6.5 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }

        val albumGainEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_ALBUM_GAIN, value=-4.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(albumGainEntry, trackGainEntry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(-6.5f))
    }

    @Test
    fun `onMetadata does not call normalizer when replay gain is invalid`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        // Use a real metadata with actual entry that has invalid gain value
        val invalidEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=not_a_number dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(invalidEntry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }
}
