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

import androidx.media3.exoplayer.ExoPlayer
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioServiceCommandRouterTest {
    @Test
    fun `stop cancels crossfade before stopping active player`() {
        val crossFadePlayer = mock<CrossFadePlayer>()
        val playbackController = mock<PlaybackController>()
        val lifecycleActions = mock<PlaybackLifecycleActions>()
        val router =
            AudioServiceCommandRouter(
                getPlaybackController = { playbackController },
                getPositionManager = { null },
                getMetadataManager = { null },
                getPlayerStateHelper = { null },
                getUnloadManager = { null },
                getActivePlayer = { mock<ExoPlayer>() },
                getCrossFadePlayer = { crossFadePlayer },
                getPlaybackLifecycleActions = { lifecycleActions },
                resetBookCompletionIfNeeded = {},
                updateCrashPlaybackContext = {},
            )

        router.stop()

        inOrder(crossFadePlayer, playbackController) {
            verify(crossFadePlayer).pause()
            verify(playbackController).stop()
        }
        verify(lifecycleActions).onStop()
    }
}
