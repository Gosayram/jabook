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
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerMetadataHandlerTest {
    private val handler =
        PlayerMetadataHandler(
            context = mock(),
            setEmbeddedArtworkPath = {},
            getActivePlayer = { mock<ExoPlayer>() },
            scope = CoroutineScope(Dispatchers.Default),
        )

    @Test
    fun `onMetadata applies track gain when both track and album tags exist`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

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

    @Test
    fun `onMetadata does not call normalizer when loudnessNormalizer is null`() {
        handler.loudnessNormalizer = null

        val trackGainEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-3.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(trackGainEntry)

        handler.onMetadata(metadata)
    }

    @Test
    fun `parseReplayGainDb extracts gain from description equals format`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-7.2 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(-7.2f))
    }

    @Test
    fun `parseReplayGainDb extracts gain from inline key value dB format`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "REPLAYGAIN_TRACK_GAIN: -4.5 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }

    @Test
    fun `parseReplayGainDb extracts gain from generic value equals dB format`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: REPLAYGAIN_TRACK_GAIN value=+2.3 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(2.3f))
    }

    @Test
    fun `onMetadata uses track gain when track tag is present`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val trackEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-8.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(trackEntry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(-8.0f))
    }

    @Test
    fun `onMetadata ignores album gain when track gain is also present`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val trackEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-5.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val albumEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_ALBUM_GAIN, value=-10.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(trackEntry, albumEntry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(-5.0f))
    }

    @Test
    fun `onMetadata applies album gain when no track gain present`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val albumEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_ALBUM_GAIN, value=-3.5 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(albumEntry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }

    @Test
    fun `parseReplayGainDb handles positive gain value`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=+1.5 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(1.5f))
    }

    @Test
    fun `parseReplayGainDb ignores malformed non numeric value`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=abc dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }

    @Test
    fun `parseReplayGainDb ignores entry with no value field`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }

    @Test
    fun `parseReplayGainDb extracts zero gain`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=0.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(0.0f))
    }

    @Test
    fun `parseReplayGainDb handles value with d suffix only`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-6.2 d"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer).setReplayGain(eq(-6.2f))
    }

    @Test
    fun `parseReplayGainDb ignores value without d suffix`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val entry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-6.2"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(entry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }

    @Test
    fun `onMetadata skips non replay gain entries`() {
        val normalizer = mock<LoudnessNormalizer>()
        handler.loudnessNormalizer = normalizer

        val otherEntry =
            object : Metadata.Entry {
                override fun toString() = "TXXX: description=SOME_OTHER_TAG, value=-5.0 dB"

                override fun equals(other: Any?) = false

                override fun hashCode() = 0
            }
        val metadata = Metadata(otherEntry)

        handler.onMetadata(metadata)

        verify(normalizer, never()).setReplayGain(any())
    }
}
