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
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReplayGainTest {
    private lateinit var playerListener: PlayerListener
    private lateinit var loudnessNormalizer: LoudnessNormalizer

    @Before
    fun setup() {
        loudnessNormalizer = mock()

        // Create PlayerListener with mocks for dependencies
        playerListener =
            PlayerListener(
                context = ApplicationProvider.getApplicationContext(),
                getActivePlayer = { mock() },
                getNotificationManager = { null },
                getIsBookCompleted = { false },
                setIsBookCompleted = { },
                getSleepTimerEndOfChapter = { false },
                getSleepTimerEndTime = { 0L },
                cancelSleepTimer = { },
                sendTimerExpiredEvent = { },
                saveCurrentPosition = { },
                startSleepTimerCheck = { },
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = { },
                getCurrentMetadata = { null },
            )

        // Inject the mock normalizer
        playerListener.loudnessNormalizer = loudnessNormalizer
    }

    @Test
    fun `onMetadata parses and applies ReplayGain`() {
        // Given a metadata entry with ReplayGain info
        val mockEntry = mock<Metadata.Entry>()
        whenever(mockEntry.toString()).thenReturn("TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-5.20 dB")

        val metadata = Metadata(mockEntry)

        // When metadata is received
        playerListener.onMetadata(metadata)

        // Then ReplayGain is set on the normalizer
        verify(loudnessNormalizer).setReplayGain(-5.20f)
    }

    @Test
    fun `onMetadata ignores non-ReplayGain entries`() {
        // Given a metadata entry with irrelevant info
        val mockEntry = mock<Metadata.Entry>()
        whenever(mockEntry.toString()).thenReturn("TIT2: value=Some Title")

        val metadata = Metadata(mockEntry)

        // When metadata is received
        playerListener.onMetadata(metadata)

        // Then ReplayGain is NOT set
        verify(loudnessNormalizer, org.mockito.kotlin.never()).setReplayGain(any())
    }
}
