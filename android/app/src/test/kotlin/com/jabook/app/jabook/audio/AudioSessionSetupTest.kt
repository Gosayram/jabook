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

import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioSessionSetupTest {
    @Test
    fun `initializeMediaSession returns early when session already exists`() {
        val service = mock<AudioPlayerService>()
        whenever(service.mediaLibrarySession).thenReturn(mock<MediaLibrarySession>())

        val setup = AudioSessionSetup(service)
        setup.initializeMediaSession()

        verify(service, never()).stopSelf()
    }

    @Test
    fun `initializeMediaSession stops service when session creation fails`() {
        val service = mock<AudioPlayerService>()
        whenever(service.mediaLibrarySession).thenReturn(null)
        whenever(service.playerPersistenceManager).thenThrow(RuntimeException("test error"))

        val setup = AudioSessionSetup(service)
        setup.initializeMediaSession()

        verify(service).stopSelf()
    }

    @Test
    fun `initializeMediaSession sets fully initialized flag on failure`() {
        val service = mock<AudioPlayerService>()
        whenever(service.mediaLibrarySession).thenReturn(null)
        whenever(service.exoPlayer).thenThrow(RuntimeException("player not available"))

        val setup = AudioSessionSetup(service)
        setup.initializeMediaSession()

        verify(service).stopSelf()
    }
}
