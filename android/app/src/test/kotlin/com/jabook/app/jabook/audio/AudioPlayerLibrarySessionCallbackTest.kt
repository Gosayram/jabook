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
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AudioPlayerLibrarySessionCallbackTest {
    private lateinit var callback: AudioPlayerLibrarySessionCallback
    private lateinit var service: AudioPlayerService
    private lateinit var session: MediaSession
    private lateinit var librarySession: MediaLibraryService.MediaLibrarySession
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
        whenever(service.playerServiceScope).thenReturn(CoroutineScope(Dispatchers.Unconfined))
        // Default durations
        whenever(mediaSessionManager.getRewindDuration()).thenReturn(15L)
        whenever(mediaSessionManager.getForwardDuration()).thenReturn(30L)

        // Mock repository flows to prevent NPE in notifyLibraryRootsChanged
        whenever(torrentRepository.getAllFlow()).thenReturn(flowOf(emptyList()))
        // Note: retrievePersistedPlayerState() is a suspend function and doesn't need mocking here
        // because notifyLibraryRootsChanged() returns early when session is not MediaLibrarySession

        session = mock()
        librarySession = mock()
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
    fun `onConnect excludes sleep timer commands for automotive controllers`() {
        whenever(session.isMediaNotificationController(controller)).thenReturn(false)
        whenever(session.isAutomotiveController(controller)).thenReturn(true)
        whenever(session.isAutoCompanionController(controller)).thenReturn(false)
        whenever(controller.packageName).thenReturn("com.android.car.media")

        val result = callback.onConnect(session, controller)

        val sleepTimerCommand =
            SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES, Bundle.EMPTY)
        val rewindCommand =
            SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_REWIND, Bundle.EMPTY)

        assertTrue(result.availableSessionCommands.contains(rewindCommand))
        assertTrue(!result.availableSessionCommands.contains(sleepTimerCommand))
    }

    @Test
    fun `onConnect includes sleep timer commands for app controller`() {
        whenever(session.isMediaNotificationController(controller)).thenReturn(false)
        whenever(session.isAutomotiveController(controller)).thenReturn(false)
        whenever(session.isAutoCompanionController(controller)).thenReturn(false)
        whenever(controller.packageName).thenReturn("com.jabook.app.jabook")

        val result = callback.onConnect(session, controller)

        val sleepTimerCommand =
            SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES, Bundle.EMPTY)
        val setPlaylistCommand =
            SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_PLAYLIST, Bundle.EMPTY)

        assertTrue(result.availableSessionCommands.contains(sleepTimerCommand))
        assertTrue(result.availableSessionCommands.contains(setPlaylistCommand))
    }

    @Test
    fun `onCustomCommand blocks automotive sleep timer command`() {
        whenever(session.isAutomotiveController(controller)).thenReturn(true)
        whenever(controller.packageName).thenReturn("com.android.car.media")

        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES,
                Bundle.EMPTY,
            )
        val args = Bundle().apply { putInt(AudioPlayerLibrarySessionCallback.ARG_MINUTES, 20) }

        val result = callback.onCustomCommand(session, controller, command, args).get(1, TimeUnit.SECONDS)

        assertEquals(SessionError.ERROR_NOT_SUPPORTED, result.resultCode)
        verify(service, never()).setSleepTimerMinutes(any())
    }

    @Test
    fun `onCustomCommand allows app sleep timer command`() {
        whenever(session.isAutomotiveController(controller)).thenReturn(false)
        whenever(controller.packageName).thenReturn("com.jabook.app.jabook")

        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES,
                Bundle.EMPTY,
            )
        val args = Bundle().apply { putInt(AudioPlayerLibrarySessionCallback.ARG_MINUTES, 20) }

        val result = callback.onCustomCommand(session, controller, command, args).get(1, TimeUnit.SECONDS)

        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        verify(service).setSleepTimerMinutes(20)
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

    @Test
    fun `onCustomCommand handles SET_PLAYLIST command through service callback`() {
        val args =
            Bundle().apply {
                putStringArray(
                    AudioPlayerLibrarySessionCallback.ARG_FILE_PATHS,
                    arrayOf("content://books/ch1.mp3", "content://books/ch2.mp3"),
                )
                putInt(AudioPlayerLibrarySessionCallback.ARG_INITIAL_TRACK_INDEX, 1)
                putLong(AudioPlayerLibrarySessionCallback.ARG_INITIAL_POSITION, 12_345L)
                putString(AudioPlayerLibrarySessionCallback.ARG_GROUP_PATH, "external://shared-audio")
            }
        val command = SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_PLAYLIST, Bundle.EMPTY)

        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.getArgument<((Boolean, Exception?) -> Unit)?>(5)
            callback?.invoke(true, null)
            Unit
        }.whenever(service).setPlaylist(
            filePaths = any(),
            metadata = anyOrNull(),
            initialTrackIndex = anyOrNull(),
            initialPosition = anyOrNull(),
            groupPath = anyOrNull(),
            callback = anyOrNull(),
        )

        val future = callback.onCustomCommand(session, controller, command, args)

        val filePathsCaptor = argumentCaptor<List<String>>()
        val metadataCaptor = argumentCaptor<Map<String, String>?>()
        val initialTrackIndexCaptor = argumentCaptor<Int?>()
        val initialPositionCaptor = argumentCaptor<Long?>()
        val groupPathCaptor = argumentCaptor<String?>()
        val callbackCaptor = argumentCaptor<((Boolean, Exception?) -> Unit)?>()
        verify(service).setPlaylist(
            filePaths = filePathsCaptor.capture(),
            metadata = metadataCaptor.capture(),
            initialTrackIndex = initialTrackIndexCaptor.capture(),
            initialPosition = initialPositionCaptor.capture(),
            groupPath = groupPathCaptor.capture(),
            callback = callbackCaptor.capture(),
        )
        assertEquals(listOf("content://books/ch1.mp3", "content://books/ch2.mp3"), filePathsCaptor.firstValue)
        assertEquals(null, metadataCaptor.firstValue)
        assertEquals(1, initialTrackIndexCaptor.firstValue)
        assertEquals(12_345L, initialPositionCaptor.firstValue)
        assertEquals("external://shared-audio", groupPathCaptor.firstValue)
        assertNotEquals(null, callbackCaptor.firstValue)

        val result = future.get(1, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    }

    @Test
    fun `onCustomCommand returns bad value when SET_PLAYLIST args are invalid`() {
        val args = Bundle.EMPTY
        val command = SessionCommand(AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_PLAYLIST, Bundle.EMPTY)

        val future = callback.onCustomCommand(session, controller, command, args)
        val result = future.get(1, TimeUnit.SECONDS)

        assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(
            "bad_value",
            result.extras.getString(SetPlaylistCommandResultPolicy.EXTRA_ERROR_REASON),
        )
        verify(service, never()).setPlaylist(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `onPlaybackResumption returns empty when current book is completed`() =
        runTest {
            whenever(service.isBookCompleted).thenReturn(true)

            val result = callback.onPlaybackResumption(session, controller).get(1, TimeUnit.SECONDS)

            assertTrue(result.mediaItems.isEmpty())
            assertEquals(0, result.startIndex)
            assertEquals(0L, result.startPositionMs)
        }

    @Test
    fun `onPlaybackResumption restores persisted playlist with start index and position`() =
        runTest {
            whenever(service.isBookCompleted).thenReturn(false)

            val context = ApplicationProvider.getApplicationContext<Context>()
            val existingFile = File(context.cacheDir, "resume_chapter_01.mp3").apply { writeText("fake-audio") }
            val missingFile = File(context.cacheDir, "resume_missing.mp3").absolutePath

            whenever(persistenceManager.retrievePersistedPlayerState()).thenReturn(
                PlayerPersistenceManager.PersistedPlayerState(
                    groupPath = "book://resume-case",
                    filePaths = listOf(existingFile.absolutePath, missingFile),
                    currentIndex = 0,
                    currentPosition = 42_000L,
                    metadata = mapOf("title" to "Resume Book", "artist" to "Narrator"),
                ),
            )

            val result = callback.onPlaybackResumption(session, controller).get(1, TimeUnit.SECONDS)

            assertEquals(1, result.mediaItems.size)
            assertEquals(existingFile.absolutePath, result.mediaItems.first().mediaId)
            assertEquals(0, result.startIndex)
            assertEquals(42_000L, result.startPositionMs)
        }

    @Test
    fun `onPlaybackResumption returns empty when no persisted and no fallback item`() =
        runTest {
            whenever(service.isBookCompleted).thenReturn(false)
            whenever(persistenceManager.retrievePersistedPlayerState()).thenReturn(null)
            whenever(persistenceManager.retrieveLastStoredMediaItem()).thenReturn(null)

            val result = callback.onPlaybackResumption(session, controller).get(1, TimeUnit.SECONDS)

            assertTrue(result.mediaItems.isEmpty())
            assertEquals(0, result.startIndex)
            assertEquals(0L, result.startPositionMs)
        }

    @Test
    fun `onGetLibraryRoot returns offline root id when offline browse params requested`() {
        val offlineParams =
            MediaLibraryService
                .LibraryParams
                .Builder()
                .setOffline(true)
                .build()

        val result =
            callback
                .onGetLibraryRoot(librarySession, controller, offlineParams)
                .get(1, TimeUnit.SECONDS)

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(AudioPlayerLibrarySessionCallback.ROOT_ID_OFFLINE, result.value?.mediaId)
    }

    @Test
    fun `onGetChildren root offline returns only downloaded and seeding torrents`() =
        runTest {
            whenever(persistenceManager.retrievePersistedPlayerState()).thenReturn(null)
            whenever(torrentRepository.getAllFlow()).thenReturn(
                flowOf(
                    listOf(
                        TorrentDownload(hash = "h1", name = "Completed", state = TorrentState.COMPLETED),
                        TorrentDownload(hash = "h2", name = "Seeding", state = TorrentState.SEEDING),
                        TorrentDownload(hash = "h3", name = "Downloading", state = TorrentState.DOWNLOADING),
                    ),
                ),
            )
            val offlineParams =
                MediaLibraryService
                    .LibraryParams
                    .Builder()
                    .setOffline(true)
                    .build()

            val result =
                callback
                    .onGetChildren(
                        librarySession,
                        controller,
                        AudioPlayerLibrarySessionCallback.ROOT_ID_OFFLINE,
                        page = 0,
                        pageSize = 100,
                        params = offlineParams,
                    ).get(1, TimeUnit.SECONDS)

            assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
            val mediaIds = result.value?.map { it.mediaId }.orEmpty()
            assertEquals(listOf("h1", "h2"), mediaIds)
        }

    @Test
    fun `onGetChildren root suggested caps to 10 items`() =
        runTest {
            whenever(persistenceManager.retrievePersistedPlayerState()).thenReturn(null)
            val downloads =
                (1..12).map { idx ->
                    TorrentDownload(
                        hash = "hash-$idx",
                        name = "Book $idx",
                        state = TorrentState.COMPLETED,
                    )
                }
            whenever(torrentRepository.getAllFlow()).thenReturn(flowOf(downloads))
            val suggestedParams =
                MediaLibraryService
                    .LibraryParams
                    .Builder()
                    .setSuggested(true)
                    .build()

            val result =
                callback
                    .onGetChildren(
                        librarySession,
                        controller,
                        AudioPlayerLibrarySessionCallback.ROOT_ID_SUGGESTED,
                        page = 0,
                        pageSize = 100,
                        params = suggestedParams,
                    ).get(1, TimeUnit.SECONDS)

            assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
            assertEquals(10, result.value?.size)
        }

    @Test
    fun `onMediaButtonEvent routes NEXT and PREVIOUS to forward and rewind`() {
        val nextIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT),
                )
            }
        val previousIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS),
                )
            }

        val nextHandled = callback.onMediaButtonEvent(session, controller, nextIntent)
        val previousHandled = callback.onMediaButtonEvent(session, controller, previousIntent)

        assertTrue(nextHandled)
        assertTrue(previousHandled)
        verify(service).forward(30)
        verify(service).rewind(15)
    }

    @Test
    fun `onMediaButtonEvent routes single double triple clicks to play pause next previous`() {
        val playPauseIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                )
            }

        callback.onMediaButtonEvent(session, controller, playPauseIntent)

        val keyCodeCaptor = argumentCaptor<Int>()
        val singleClickCaptor = argumentCaptor<() -> Unit>()
        val doubleClickCaptor = argumentCaptor<() -> Unit>()
        val tripleClickCaptor = argumentCaptor<() -> Unit>()
        verify(mediaButtonHandler).onMediaButtonEvent(
            keyCodeCaptor.capture(),
            onSingleClick = singleClickCaptor.capture(),
            onDoubleClick = doubleClickCaptor.capture(),
            onTripleClick = tripleClickCaptor.capture(),
        )
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, keyCodeCaptor.firstValue)

        whenever(service.isPlaying).thenReturn(false, true)

        singleClickCaptor.firstValue.invoke()
        singleClickCaptor.firstValue.invoke()
        doubleClickCaptor.firstValue.invoke()
        tripleClickCaptor.firstValue.invoke()

        verify(service).play()
        verify(service).pause()
        verify(service).next()
        verify(service).previous()
    }

    @Test
    fun `onMediaButtonEvent ignores ACTION_UP events`() {
        val actionUpIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                )
            }

        callback.onMediaButtonEvent(session, controller, actionUpIntent)

        verify(mediaButtonHandler, never()).onMediaButtonEvent(any(), any(), any(), any())
        verify(service, never()).play()
        verify(service, never()).pause()
        verify(service, never()).next()
        verify(service, never()).previous()
    }
}
