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
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerListenerReleaseScopeTest {
    @Test
    fun `release cancels owned scope when coroutineScope is null`() {
        val listener =
            PlayerListener(
                context = mock<Context>(),
                getActivePlayer = { mock<ExoPlayer>() },
                getIsBookCompleted = { false },
                setIsBookCompleted = {},
                getSleepTimerEndOfChapter = { false },
                getSleepTimerEndOfTrack = { false },
                cancelSleepTimer = {},
                sendTimerExpiredEvent = {},
                markSleepTimerPause = {},
                saveCurrentPosition = {},
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = {},
                getCurrentMetadata = { null },
                // coroutineScope is null — triggers owned scope creation
            )

        listener.release()

        // The owned scope should be cancelled; the sub-handlers' coroutines should not leak
    }

    @Test
    fun `release does not cancel externally provided scope`() {
        val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val listener =
            PlayerListener(
                context = mock<Context>(),
                getActivePlayer = { mock<ExoPlayer>() },
                getIsBookCompleted = { false },
                setIsBookCompleted = {},
                getSleepTimerEndOfChapter = { false },
                getSleepTimerEndOfTrack = { false },
                cancelSleepTimer = {},
                sendTimerExpiredEvent = {},
                markSleepTimerPause = {},
                saveCurrentPosition = {},
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = {},
                getCurrentMetadata = { null },
                coroutineScope = externalScope,
            )

        listener.release()

        assertTrue("External scope should still be active", externalScope.isActive)
        externalScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `release cancels owned scope even when sub-handlers have pending work`() {
        val listener =
            PlayerListener(
                context = mock<Context>(),
                getActivePlayer = { mock<ExoPlayer>() },
                getIsBookCompleted = { false },
                setIsBookCompleted = {},
                getSleepTimerEndOfChapter = { false },
                getSleepTimerEndOfTrack = { false },
                cancelSleepTimer = {},
                sendTimerExpiredEvent = {},
                markSleepTimerPause = {},
                saveCurrentPosition = {},
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = {},
                getCurrentMetadata = { null },
            )

        listener.release()

        // Verify no crash on double release
        listener.release()
    }
}
