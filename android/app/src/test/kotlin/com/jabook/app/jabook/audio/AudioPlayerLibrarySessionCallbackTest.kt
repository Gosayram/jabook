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
import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.audio.MediaSessionManager
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AudioPlayerLibrarySessionCallbackTest {
    private lateinit var callback: AudioPlayerLibrarySessionCallback
    private lateinit var service: AudioPlayerService
    private lateinit var session: MediaSession
    private lateinit var controller: MediaSession.ControllerInfo
    private lateinit var persistenceManager: PlayerPersistenceManager
    private lateinit var torrentRepository: TorrentDownloadRepository
    private lateinit var mediaButtonHandler: MediaButtonHandler
    private lateinit var mediaSessionManager: MediaSessionManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        service = mock()
        whenever(service.applicationContext).thenReturn(context)
        whenever(service.packageName).thenReturn("com.jabook.app.jabook")
        // Mock string resources
        whenever(service.getString(any())).thenReturn("Test String")

        persistenceManager = mock()
        torrentRepository = mock()
        mediaButtonHandler = mock()
        mediaSessionManager = mock()

        // Setup service mocks
        whenever(service.mediaSessionManager).thenReturn(mediaSessionManager)
        // Default durations
        whenever(mediaSessionManager.getRewindDuration()).thenReturn(15L)
        whenever(mediaSessionManager.getForwardDuration()).thenReturn(30L)

        session = mock()
        controller = mock()
        whenever(controller.packageName).thenReturn("com.jabook.app.jabook")

        callback =
            AudioPlayerLibrarySessionCallback(
                service,
                persistenceManager,
                torrentRepository,
                mediaButtonHandler,
                { null }, // Duration provider mock
            )
    }

    @Test
    fun `onCustomCommand handles REWIND command`() {
        // Given
        val command = SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_REWIND, Bundle.EMPTY)

        // When
        val future = callback.onCustomCommand(session, controller, command, Bundle.EMPTY)

        // Then
        verify(service).rewind(15)
        val result = future.get(1, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    }

    @Test
    fun `onCustomCommand handles FORWARD command`() {
        // Given
        val command = SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_FORWARD, Bundle.EMPTY)

        // When
        val future = callback.onCustomCommand(session, controller, command, Bundle.EMPTY)

        // Then
        verify(service).forward(30)
        val result = future.get(1, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    }

    @Test
    fun `onCustomCommand returns ERROR_NOT_SUPPORTED for unknown command`() {
        // Given
        val command = SessionCommand("unknown.command", Bundle.EMPTY)

        // When
        val future = callback.onCustomCommand(session, controller, command, Bundle.EMPTY)

        // Then
        // Should not call service methods
        // Result code check might depend on implementation details, usually it's ERROR_NOT_SUPPORTED equivalent
        // Implementation returns SessionResult(SessionError.ERROR_NOT_SUPPORTED) -> code is non-zero
        val result = future.get(1, TimeUnit.SECONDS)
        assert(result.resultCode != SessionResult.RESULT_SUCCESS)
    }

    @Test
    fun `onCustomCommand denies privileged command for non app controller`() {
        // Given
        whenever(controller.packageName).thenReturn("com.external.controller")
        val args =
            Bundle().apply {
                putBoolean(AudioPlayerLibrarySessionCallback.ARG_VISUALIZER_ENABLED, true)
            }
        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_VISUALIZER_ENABLED,
                Bundle.EMPTY,
            )

        // When
        val future = callback.onCustomCommand(session, controller, command, args)

        // Then
        verify(service, never()).setVisualizerEnabled(any())
        val result = future.get(1, TimeUnit.SECONDS)
        assert(result.resultCode != SessionResult.RESULT_SUCCESS)
    }

    @Test
    fun `onCustomCommand allows privileged command for app controller`() {
        // Given
        whenever(controller.packageName).thenReturn("com.jabook.app.jabook")
        val args =
            Bundle().apply {
                putBoolean(AudioPlayerLibrarySessionCallback.ARG_VISUALIZER_ENABLED, true)
            }
        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_VISUALIZER_ENABLED,
                Bundle.EMPTY,
            )

        // When
        val future = callback.onCustomCommand(session, controller, command, args)

        // Then
        verify(service).setVisualizerEnabled(true)
        val result = future.get(1, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    }
}
