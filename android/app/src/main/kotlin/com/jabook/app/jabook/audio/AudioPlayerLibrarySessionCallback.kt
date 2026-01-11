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

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Callback implementation for Media3 MediaLibraryService session.
 *
 * Handles media library operations such as:
 * - Browsing media items (books, chapters)
 * - Getting item metadata
 * - Subscribing to item changes
 * - Handling media button events
 *
 * @param service The AudioPlayerService instance
 * @param playerPersistenceManager Manages player state persistence
 * @param torrentDownloadRepository Repository for torrent downloads
 * @param mediaButtonHandler Handler for media button events
 * @param getDurationForFile Function to get duration for a file path
 */
public class AudioPlayerLibrarySessionCallback(
    private val service: AudioPlayerService,
    private val playerPersistenceManager: PlayerPersistenceManager,
    private val torrentDownloadRepository: com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository,
    private val mediaButtonHandler: MediaButtonHandler?,
    private val getDurationForFile: (String) -> Long?,
) : MediaLibraryService.MediaLibrarySession.Callback {
    public val customCommands: List<CommandButton> =
        listOf(
            CommandButton
                .Builder(CommandButton.ICON_SKIP_BACK)
                .setDisplayName(service.getString(com.jabook.app.jabook.R.string.rewind))
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(CUSTOM_COMMAND_REWIND, Bundle.EMPTY),
                ).build(),
            CommandButton
                .Builder(CommandButton.ICON_SKIP_FORWARD)
                .setDisplayName(service.getString(com.jabook.app.jabook.R.string.forward))
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(CUSTOM_COMMAND_FORWARD, Bundle.EMPTY),
                ).build(),
        )

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        // Following Media3 official pattern: only add custom commands for system controllers
        // (notification, automotive, auto companion). Regular app controllers get default commands.
        //
        // CRITICAL FIX: Removed setCustomLayout() from onConnect() (following Rhythm pattern)
        // Reason: Media3's MediaSessionLegacyStub cannot properly convert CommandButton to
        // PlaybackStateCompat.CustomAction during controller connection, causing crashes.
        // CustomLayout is now set separately after MediaController initialization to avoid
        // timing issues with MediaSessionLegacyStub conversion.
        //
        // Custom commands (rewind/forward) are still available via SessionCommands,
        // and CustomLayout will be set after initialization completes.
        if (
            session.isMediaNotificationController(controller) ||
            session.isAutomotiveController(controller) ||
            session.isAutoCompanionController(controller)
        ) {
            val rewindCommand = androidx.media3.session.SessionCommand(CUSTOM_COMMAND_REWIND, Bundle.EMPTY)
            val forwardCommand = androidx.media3.session.SessionCommand(CUSTOM_COMMAND_FORWARD, Bundle.EMPTY)

            val availableCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SET_PLAYLIST, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_CHAPTER, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_GET_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_IS_SLEEP_TIMER_ACTIVE, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_IS_SLEEP_TIMER_END_OF_CHAPTER, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_GET_CURRENT_GROUP_PATH, Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_GET_CURRENT_FILE_PATHS, Bundle.EMPTY))
                    .build()

            // NOTE: CustomLayout is NOT set here - it will be set separately after initialization
            // This follows Rhythm pattern to avoid MediaSessionLegacyStub conversion issues
            return MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .build()
        }

        // For regular app controllers, add custom commands but without custom buttons
        val availableCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_REWIND, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_FORWARD, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SET_PLAYLIST, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_CHAPTER, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_GET_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_IS_SLEEP_TIMER_ACTIVE, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_IS_SLEEP_TIMER_END_OF_CHAPTER, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_GET_CURRENT_GROUP_PATH, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_GET_CURRENT_FILE_PATHS, Bundle.EMPTY))
                .build()

        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableCommands)
            .build()
    }

    /**
     * Handles media button events from physical buttons (e.g., headphones, Bluetooth devices).
     *
     * Inspired by lissen-android implementation for better hardware button support.
     */
    override fun onMediaButtonEvent(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        android.util.Log.d("AudioPlayerService", "Media button event from: $controller")

        val keyEvent =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            } ?: return super.onMediaButtonEvent(session, controller, intent)

        android.util.Log.d("AudioPlayerService", "Got media key event: $keyEvent")

        // Only handle ACTION_DOWN events to avoid duplicate handling
        if (keyEvent.action != KeyEvent.ACTION_DOWN) {
            return super.onMediaButtonEvent(session, controller, intent)
        }

        when (keyEvent.keyCode) {
            KEYCODE_MEDIA_NEXT -> {
                val forwardSeconds = service.mediaSessionManager?.getForwardDuration()?.toInt() ?: 30
                service.forward(forwardSeconds)
                android.util.Log.d("AudioPlayerService", "Media button: forward ${forwardSeconds}s")
                return true
            }
            KEYCODE_MEDIA_PREVIOUS -> {
                val rewindSeconds = service.mediaSessionManager?.getRewindDuration()?.toInt() ?: 15
                service.rewind(rewindSeconds)
                android.util.Log.d("AudioPlayerService", "Media button: rewind ${rewindSeconds}s")
                return true
            }
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> {
                mediaButtonHandler?.onMediaButtonEvent(
                    keyEvent.keyCode,
                    onSingleClick = {
                        if (service.isPlaying) service.pause() else service.play()
                        android.util.Log.d("AudioPlayerService", "Media button: Single click (Play/Pause)")
                    },
                    onDoubleClick = {
                        service.next()
                        android.util.Log.d("AudioPlayerService", "Media button: Double click (Next)")
                    },
                    onTripleClick = {
                        service.previous()
                        android.util.Log.d("AudioPlayerService", "Media button: Triple click (Previous)")
                    },
                )
                return true
            }
            else -> return super.onMediaButtonEvent(session, controller, intent)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> =
        when (customCommand.customAction) {
            CUSTOM_COMMAND_REWIND -> {
                val rewindSeconds = service.mediaSessionManager?.getRewindDuration()?.toInt() ?: 15
                service.rewind(rewindSeconds)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_FORWARD -> {
                val forwardSeconds = service.mediaSessionManager?.getForwardDuration()?.toInt() ?: 30
                service.forward(forwardSeconds)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_SET_PLAYLIST -> {
                handleSetPlaylistCommand(args)
            }
            CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES -> {
                val minutes = args.getInt(ARG_MINUTES, 0)
                if (minutes > 0) {
                    service.setSleepTimerMinutes(minutes)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }
            }
            CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_CHAPTER -> {
                service.setSleepTimerEndOfChapter()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_CANCEL_SLEEP_TIMER -> {
                service.cancelSleepTimer()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_GET_SLEEP_TIMER_REMAINING -> {
                val remaining = service.getSleepTimerRemainingSeconds()
                val resultBundle =
                    Bundle().apply {
                        if (remaining != null) {
                            putInt(ARG_RESULT_REMAINING, remaining)
                        }
                    }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
            }
            CUSTOM_COMMAND_IS_SLEEP_TIMER_ACTIVE -> {
                val isActive = service.isSleepTimerActive()
                val resultBundle =
                    Bundle().apply {
                        putBoolean(ARG_RESULT_ACTIVE, isActive)
                    }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
            }
            CUSTOM_COMMAND_IS_SLEEP_TIMER_END_OF_CHAPTER -> {
                val isEndOfChapter = service.isSleepTimerEndOfChapter()
                val resultBundle =
                    Bundle().apply {
                        putBoolean(ARG_RESULT_END_OF_CHAPTER, isEndOfChapter)
                    }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
            }
            CUSTOM_COMMAND_GET_CURRENT_GROUP_PATH -> {
                val groupPath = service.currentGroupPath
                val resultBundle =
                    Bundle().apply {
                        if (groupPath != null) {
                            putString(ARG_RESULT_GROUP_PATH, groupPath)
                        }
                    }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
            }
            CUSTOM_COMMAND_GET_CURRENT_FILE_PATHS -> {
                val filePaths = service.currentFilePaths
                val resultBundle =
                    Bundle().apply {
                        if (filePaths != null) {
                            putStringArray(ARG_RESULT_FILE_PATHS, filePaths.toTypedArray())
                        }
                    }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
            }
            else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }

    /**
     * Handles setPlaylist command with complex parameters.
     * Uses coroutines to handle async callback.
     */
    private fun handleSetPlaylistCommand(args: Bundle): ListenableFuture<SessionResult> =
        CoroutineScope(Dispatchers.IO).future {
            try {
                val filePathsArray = args.getStringArray(ARG_FILE_PATHS)
                if (filePathsArray == null) {
                    return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                }
                val filePaths = filePathsArray.toList()

                // Extract metadata if present, converting Map<String!, String?>? to Map<String, String>?
                public val metadataMap: Map<String, String>? =
                    args.getBundle(ARG_METADATA)?.let { metadataBundle ->
                        metadataBundle
                            .keySet()
                            .associateWith { key ->
                                metadataBundle.getString(key) ?: ""
                            }.filterValues { it.isNotEmpty() }
                    }

                val initialTrackIndex =
                    if (args.containsKey(ARG_INITIAL_TRACK_INDEX)) {
                        args.getInt(ARG_INITIAL_TRACK_INDEX)
                    } else {
                        null
                    }

                val initialPosition =
                    if (args.containsKey(ARG_INITIAL_POSITION)) {
                        args.getLong(ARG_INITIAL_POSITION)
                    } else {
                        null
                    }

                val groupPath = args.getString(ARG_GROUP_PATH)

                // Use CompletableDeferred to wait for callback
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()

                service.setPlaylist(
                    filePaths = filePaths,
                    metadata = metadataMap,
                    initialTrackIndex = initialTrackIndex,
                    initialPosition = initialPosition,
                    groupPath = groupPath,
                    callback = { success, exception ->
                        if (exception != null) {
                            android.util.Log.e("AudioPlayerService", "setPlaylist failed", exception)
                        }
                        deferred.complete(success)
                    },
                )

                // Wait for callback with timeout
                val success =
                    try {
                        withTimeout(30000) {
                            // 30 seconds timeout
                            deferred.await()
                        }
                    } catch (e: TimeoutCancellationException) {
                        android.util.Log.e("AudioPlayerService", "setPlaylist timeout", e)
                        false
                    }

                if (success) {
                    SessionResult(SessionResult.RESULT_SUCCESS)
                } else {
                    SessionResult(SessionError.ERROR_UNKNOWN)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Error in handleSetPlaylistCommand", e)
                SessionResult(SessionError.ERROR_UNKNOWN)
            }
        }

    public companion object {
        public const val CUSTOM_COMMAND_REWIND: String = "com.jabook.app.jabook.rewind"
        public const val CUSTOM_COMMAND_FORWARD: String = "com.jabook.app.jabook.forward"

        // Playlist management commands
        public const val CUSTOM_COMMAND_SET_PLAYLIST: String = "com.jabook.app.jabook.setPlaylist"

        // Sleep timer commands
        public const val CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES: String = "com.jabook.app.jabook.setSleepTimerMinutes"
        public const val CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_CHAPTER: String = "com.jabook.app.jabook.setSleepTimerEndOfChapter"
        public const val CUSTOM_COMMAND_CANCEL_SLEEP_TIMER: String = "com.jabook.app.jabook.cancelSleepTimer"
        public const val CUSTOM_COMMAND_GET_SLEEP_TIMER_REMAINING: String = "com.jabook.app.jabook.getSleepTimerRemaining"
        public const val CUSTOM_COMMAND_IS_SLEEP_TIMER_ACTIVE: String = "com.jabook.app.jabook.isSleepTimerActive"
        public const val CUSTOM_COMMAND_IS_SLEEP_TIMER_END_OF_CHAPTER: String = "com.jabook.app.jabook.isSleepTimerEndOfChapter"

        // Service state commands
        public const val CUSTOM_COMMAND_GET_CURRENT_GROUP_PATH: String = "com.jabook.app.jabook.getCurrentGroupPath"
        public const val CUSTOM_COMMAND_GET_CURRENT_FILE_PATHS: String = "com.jabook.app.jabook.getCurrentFilePaths"

        // Bundle keys for command arguments
        public const val ARG_FILE_PATHS: String = "filePaths"
        public const val ARG_METADATA: String = "metadata"
        public const val ARG_INITIAL_TRACK_INDEX: String = "initialTrackIndex"
        public const val ARG_INITIAL_POSITION: String = "initialPosition"
        public const val ARG_GROUP_PATH: String = "groupPath"
        public const val ARG_MINUTES: String = "minutes"
        public const val ARG_RESULT_REMAINING: String = "remaining"
        public const val ARG_RESULT_ACTIVE: String = "active"
        public const val ARG_RESULT_END_OF_CHAPTER: String = "endOfChapter"
        public const val ARG_RESULT_GROUP_PATH: String = "groupPath"
        public const val ARG_RESULT_FILE_PATHS: String = "filePaths"
    }

    // Minimal implementation for library operations (required by MediaLibrarySession.Callback)
    // These are not used in our case, but must be implemented
    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        CoroutineScope(Dispatchers.IO).future {
            // Log recommended media art size for optimization (Android Auto provides size hints)
            val recommendedArtSize = MediaMetadataExtrasHelper.getRecommendedArtSize(params)
            android.util.Log.d(
                "LibrarySession",
                "Recommended media art size: ${recommendedArtSize}px (from Android Auto/browser)",
            )

            // Create content style preferences for Android Auto
            val rootExtras = MediaMetadataExtrasHelper.createRootExtras()

            val rootItem =
                MediaItem
                    .Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setTitle(service.getString(com.jabook.app.jabook.R.string.media3_library_root_title))
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .setExtras(rootExtras)
                            .build(),
                    ).build()

            // Return root with style preferences in params too
            val resultParams =
                MediaLibraryService.LibraryParams
                    .Builder()
                    .setExtras(rootExtras)
                    .build()

            LibraryResult.ofItem(rootItem, resultParams)
        }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        CoroutineScope(Dispatchers.IO).future {
            if (mediaId == "root") {
                val rootItem =
                    MediaItem
                        .Builder()
                        .setMediaId("root")
                        .setMediaMetadata(
                            MediaMetadata
                                .Builder()
                                .setTitle(service.getString(com.jabook.app.jabook.R.string.media3_library_root_title))
                                .setIsBrowsable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .build(),
                        ).build()
                return@future LibraryResult.ofItem(rootItem, null)
            }

            // 1. Check Persisted State (Last Played) - Fast Path
            val persistedState = playerPersistenceManager.retrievePersistedPlayerState()
            if (persistedState != null) {
                if (mediaId == persistedState.groupPath) {
                    val bookTitle = persistedState.metadata?.get("title") ?: "Last Played"
                    val metadataBuilder =
                        MediaMetadata
                            .Builder()
                            .setTitle(bookTitle)
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)

                    persistedState.metadata?.get("artist")?.let { metadataBuilder.setArtist(it) }
                    persistedState.metadata?.get("coverPath")?.let { coverPath ->
                        if (coverPath.isNotEmpty()) {
                            val artworkFile = File(coverPath)
                            if (artworkFile.exists()) {
                                metadataBuilder.setArtworkUri(android.net.Uri.fromFile(artworkFile))
                            }
                        }
                    }

                    val bookItem =
                        MediaItem
                            .Builder()
                            .setMediaId(persistedState.groupPath)
                            .setMediaMetadata(metadataBuilder.build())
                            .build()
                    return@future LibraryResult.ofItem(bookItem, null)
                }
            }

            // 2. Check Repository for Download (Album/Book)
            val download = torrentDownloadRepository.getByHash(mediaId)
            if (download != null) {
                val metadataBuilder =
                    MediaMetadata
                        .Builder()
                        .setTitle(download.name)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)

                // Add extras if needed (download status etc.)
                val extras =
                    Bundle().apply {
                        val isDownloaded =
                            download.state == com.jabook.app.jabook.compose.data.torrent.TorrentState.COMPLETED ||
                                download.state == com.jabook.app.jabook.compose.data.torrent.TorrentState.SEEDING
                        MediaMetadataExtrasHelper.run { addDownloadStatus(isDownloaded) }
                    }
                metadataBuilder.setExtras(extras)

                val item =
                    LibraryResult.ofItem(
                        MediaItem
                            .Builder()
                            .setMediaId(download.hash)
                            .setMediaMetadata(metadataBuilder.build())
                            .build(),
                        null,
                    )
                return@future item
            }

            // 3. Check for File (Chapter/Track)
            // This covers files in persisted state AND generic files from repository
            val file = File(mediaId)
            if (file.exists() && file.isFile) {
                val chapterMetadata =
                    MediaMetadata
                        .Builder()
                        .setTitle(file.nameWithoutExtension)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()

                val item =
                    LibraryResult.ofItem(
                        MediaItem
                            .Builder()
                            .setMediaId(mediaId)
                            .setUri(android.net.Uri.fromFile(file))
                            .setMediaMetadata(chapterMetadata)
                            .build(),
                        null,
                    )
                return@future item
            }

            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        CoroutineScope(Dispatchers.IO).future {
            val persistedState = playerPersistenceManager.retrievePersistedPlayerState()
            val items = mutableListOf<MediaItem>()

            // 1. "Last Played" Item
            if (parentId == "root" && persistedState != null) {
                val bookTitle = persistedState.metadata?.get("title") ?: "Last Played"
                val metadataBuilder =
                    MediaMetadata
                        .Builder()
                        .setTitle(bookTitle)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)

                persistedState.metadata?.get("artist")?.let { metadataBuilder.setArtist(it) }
                persistedState.metadata?.get("coverPath")?.let { coverPath ->
                    if (coverPath.isNotEmpty()) {
                        val artworkFile = File(coverPath)
                        if (artworkFile.exists()) {
                            metadataBuilder.setArtworkUri(android.net.Uri.fromFile(artworkFile))
                        }
                    }
                }

                // Create comprehensive metadata extras (completion, download status, grouping, etc.)
                val totalDuration = persistedState.filePaths.sumOf { getDurationForFile(it) ?: 0L }
                val metadataExtras =
                    CompletionStatusHelper
                        .createCompletionExtras(
                            positionMs = persistedState.currentPosition,
                            durationMs = totalDuration,
                        ).apply {
                            // Download status: check if files exist locally
                            val isDownloaded = persistedState.filePaths.all { File(it).exists() }
                            MediaMetadataExtrasHelper.run { addDownloadStatus(isDownloaded) }

                            // Content grouping for series
                            persistedState.metadata?.get("series")?.let { series ->
                                MediaMetadataExtrasHelper.run { addContentGroup(series) }
                            }

                            // Explicit content flag
                            val isExplicit = persistedState.metadata?.get("isExplicit")?.toBoolean() ?: false
                            MediaMetadataExtrasHelper.run { addExplicitFlag(isExplicit) }

                            // Grid view for books with cover art
                            MediaMetadataExtrasHelper.run {
                                addPlayableStyle(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                            }
                        }
                metadataBuilder.setExtras(metadataExtras)

                items.add(
                    MediaItem
                        .Builder()
                        .setMediaId(persistedState.groupPath)
                        .setMediaMetadata(metadataBuilder.build())
                        .build(),
                )
            }

            // 2. All Downloads from Repository
            if (parentId == "root") {
                try {
                    val downloads = torrentDownloadRepository.getAllFlow().firstOrNull() ?: emptyList()

                    for (download in downloads) {
                        // Skip if it's the same as Last Played to avoid duplicates
                        if (persistedState != null && download.hash == persistedState.groupPath) continue

                        val metadataBuilder =
                            MediaMetadata
                                .Builder()
                                .setTitle(download.name)
                                .setIsBrowsable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)

                        // Set cover if available (assuming cover path might be in download path or handled elsewhere)
                        // For now we don't have direct cover path in TorrentDownload model easily accessible without parsing
                        // But if we did, we would set it here.

                        val extras =
                            Bundle().apply {
                                // Download status
                                val isDownloaded =
                                    download.state == com.jabook.app.jabook.compose.data.torrent.TorrentState.COMPLETED ||
                                        download.state == com.jabook.app.jabook.compose.data.torrent.TorrentState.SEEDING
                                MediaMetadataExtrasHelper.run { addDownloadStatus(isDownloaded) }

                                MediaMetadataExtrasHelper.run {
                                    addPlayableStyle(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                                }
                            }
                        metadataBuilder.setExtras(extras)

                        items.add(
                            MediaItem
                                .Builder()
                                .setMediaId(download.hash)
                                .setMediaMetadata(metadataBuilder.build())
                                .build(),
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibrarySession", "Failed to load downloads", e)
                }
            } else if (parentId != "root") {
                // 3. Browse specific book (by Hash)
                val download = torrentDownloadRepository.getByHash(parentId)
                if (download != null) {
                    val sortedFiles = download.files.sortedBy { it.path }
                    for (file in sortedFiles) {
                        val f = File(file.path)
                        val chapterMetadata =
                            MediaMetadata
                                .Builder()
                                .setTitle(f.name)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build()

                        items.add(
                            MediaItem
                                .Builder()
                                .setMediaId(file.path)
                                .setUri(android.net.Uri.fromFile(f))
                                .setMediaMetadata(chapterMetadata)
                                .build(),
                        )
                    }
                } else if (persistedState != null && parentId == persistedState.groupPath) {
                    // Fallback to persisted state
                    for (filePath in persistedState.filePaths) {
                        val file = File(filePath)
                        if (file.exists()) {
                            val chapterMetadata =
                                MediaMetadata
                                    .Builder()
                                    .setTitle(file.nameWithoutExtension)
                                    .setIsBrowsable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()

                            items.add(
                                MediaItem
                                    .Builder()
                                    .setMediaId(filePath)
                                    .setUri(android.net.Uri.fromFile(file))
                                    .setMediaMetadata(chapterMetadata)
                                    .build(),
                            )
                        }
                    }
                }
            }

            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> =
        CoroutineScope(Dispatchers.IO).future {
            val persistedState = playerPersistenceManager.retrievePersistedPlayerState()
            var count = 0
            if (persistedState != null) {
                val title = persistedState.metadata?.get("title") ?: ""
                // Simple search implementation checking title
                if (title.contains(query, ignoreCase = true)) {
                    count = 1
                }
            }

            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid(params)
        }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        CoroutineScope(Dispatchers.IO).future {
            val persistedState = playerPersistenceManager.retrievePersistedPlayerState()
            val items = mutableListOf<MediaItem>()

            if (persistedState != null) {
                val title = persistedState.metadata?.get("title") ?: ""
                if (title.contains(query, ignoreCase = true)) {
                    val bookTitle = persistedState.metadata?.get("title") ?: "Last Played"
                    val metadataBuilder =
                        MediaMetadata
                            .Builder()
                            .setTitle(bookTitle)
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)

                    persistedState.metadata?.get("artist")?.let { metadataBuilder.setArtist(it) }
                    persistedState.metadata?.get("coverPath")?.let { coverPath ->
                        if (coverPath.isNotEmpty()) {
                            val artworkFile = File(coverPath)
                            if (artworkFile.exists()) {
                                metadataBuilder.setArtworkUri(android.net.Uri.fromFile(artworkFile))
                            }
                        }
                    }

                    // Add comprehensive metadata for search results
                    val totalDuration = persistedState.filePaths.sumOf { getDurationForFile(it) ?: 0L }
                    val metadataExtras =
                        CompletionStatusHelper
                            .createCompletionExtras(
                                positionMs = persistedState.currentPosition,
                                durationMs = totalDuration,
                            ).apply {
                                val isDownloaded = persistedState.filePaths.all { File(it).exists() }
                                MediaMetadataExtrasHelper.run { addDownloadStatus(isDownloaded) }
                                persistedState.metadata?.get("series")?.let { MediaMetadataExtrasHelper.run { addContentGroup(it) } }
                                val isExplicit = persistedState.metadata?.get("isExplicit")?.toBoolean() ?: false
                                MediaMetadataExtrasHelper.run { addExplicitFlag(isExplicit) }
                            }
                    metadataBuilder.setExtras(metadataExtras)

                    items.add(
                        MediaItem
                            .Builder()
                            .setMediaId(persistedState.groupPath)
                            .setMediaMetadata(metadataBuilder.build())
                            .build(),
                    )
                }
            }

            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

    /**
     * Handles playback resumption when user taps on media item in Quick Settings carousel.
     *
     * This is called by the system when user wants to resume playback from a previous session.
     * Based on Media3 DemoMediaLibrarySessionCallback example.
     */
    @OptIn(UnstableApi::class) // onPlaybackResumption callback + MediaItemsWithStartPosition
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        CoroutineScope(Dispatchers.Unconfined).future {
            // Check if book is completed - don't resume completed books
            if (service.isBookCompleted) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Book is completed, skipping playback resumption",
                )
                return@future MediaSession.MediaItemsWithStartPosition(
                    emptyList(),
                    0,
                    0L,
                )
            }

            // Try to load full playlist state first (New Logic)
            val persistedState = playerPersistenceManager.retrievePersistedPlayerState()

            if (persistedState != null && persistedState.filePaths.isNotEmpty()) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Resuming playback from persisted full state: group=${persistedState.groupPath}, " +
                        "tracks=${persistedState.filePaths.size}, index=${persistedState.currentIndex}",
                )

                val playlist = mutableListOf<MediaItem>()
                for (filePath in persistedState.filePaths) {
                    val file = File(filePath)
                    if (file.exists()) {
                        val uri = android.net.Uri.fromFile(file)
                        val metadataBuilder = MediaMetadata.Builder()

                        // Use filename as default title
                        var title = file.nameWithoutExtension

                        // If this is the current item, add extra metadata if available
                        if (persistedState.filePaths.indexOf(filePath) == persistedState.currentIndex) {
                            // Add completion extras
                            val durationMs = getDurationForFile(filePath) ?: 0L
                            if (durationMs > 0) {
                                val extras = Bundle()
                                extras.putInt(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
                                )
                                val completionPercentage =
                                    kotlin.math.max(
                                        0.0,
                                        kotlin.math.min(
                                            1.0,
                                            persistedState.currentPosition.toDouble() / durationMs,
                                        ),
                                    )
                                extras.putDouble(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                                    completionPercentage,
                                )
                                metadataBuilder.setExtras(extras)

                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Added completion extras: ${completionPercentage * 100}%",
                                )
                            }

                            // Add metadata if available
                            persistedState.metadata?.get("artist")?.let {
                                if (it.isNotEmpty()) metadataBuilder.setArtist(it)
                            }
                            persistedState.metadata?.get("title")?.let {
                                if (it.isNotEmpty()) {
                                    metadataBuilder.setTitle(it)
                                    title = it
                                }
                            }

                            // Add artwork
                            persistedState.metadata?.get("coverPath")?.let { coverPath ->
                                if (coverPath.isNotEmpty()) {
                                    val artworkFile = File(coverPath)
                                    if (artworkFile.exists()) {
                                        metadataBuilder.setArtworkUri(android.net.Uri.fromFile(artworkFile))
                                    }
                                }
                            }
                        } else {
                            // For other items, we might not have specific metadata loaded yet
                            // Just set title from filename
                            metadataBuilder.setTitle(title)
                        }

                        playlist.add(
                            MediaItem
                                .Builder()
                                .setUri(uri)
                                .setMediaId(filePath)
                                .setMediaMetadata(metadataBuilder.build())
                                .build(),
                        )
                    } else {
                        android.util.Log.w(
                            "AudioPlayerService",
                            "Skipping missing file in restored playlist: $filePath",
                        )
                    }
                }

                if (playlist.isNotEmpty()) {
                    var correctedIndex = persistedState.currentIndex
                    if (persistedState.currentIndex < persistedState.filePaths.size) {
                        val currentFilePath = persistedState.filePaths[persistedState.currentIndex]
                        val newIndex = playlist.indexOfFirst { it.mediaId == currentFilePath }
                        if (newIndex >= 0) {
                            correctedIndex = newIndex
                        } else {
                            correctedIndex = 0
                        }
                    } else {
                        correctedIndex = 0
                    }

                    correctedIndex = correctedIndex.coerceIn(0, playlist.size - 1)

                    android.util.Log.i(
                        "AudioPlayerService",
                        "Restoring full playlist: ${playlist.size} items, index=$correctedIndex",
                    )

                    return@future MediaSession.MediaItemsWithStartPosition(
                        playlist,
                        correctedIndex,
                        persistedState.currentPosition,
                    )
                }
            }

            val storedData = playerPersistenceManager.retrieveLastStoredMediaItem()
            if (storedData == null) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "No stored media item found for playback resumption, returning empty list",
                )
                return@future MediaSession.MediaItemsWithStartPosition(
                    emptyList(),
                    0,
                    0L,
                )
            }

            val filePath =
                storedData["filePath"] as? String
                    ?: throw IllegalStateException("stored file path is null")
            val positionMs = storedData["positionMs"] as? Long ?: 0L
            val durationMs = storedData["durationMs"] as? Long ?: 0L
            val artworkPath = storedData["artworkPath"] as? String ?: ""
            val title = storedData["title"] as? String ?: ""
            val artist = storedData["artist"] as? String ?: ""
            val groupPath = storedData["groupPath"] as? String ?: ""

            android.util.Log.d(
                "AudioPlayerService",
                "Resuming playback (fallback): filePath=$filePath, position=${positionMs}ms, duration=${durationMs}ms",
            )

            // Create extras for completion status
            var extras: Bundle? = null
            if (durationMs != C.TIME_UNSET && durationMs > 0) {
                extras = Bundle()
                extras.putInt(
                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
                )
                val completionPercentage =
                    kotlin.math.max(
                        0.0,
                        kotlin.math.min(1.0, positionMs.toDouble() / durationMs),
                    )
                extras.putDouble(
                    MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                    completionPercentage,
                )
            }

            // Create MediaMetadata with artwork and completion info
            val metadataBuilder =
                MediaMetadata
                    .Builder()
                    .setTitle(title.takeIf { it.isNotEmpty() } ?: File(filePath).nameWithoutExtension)
                    .setArtist(artist.takeIf { it.isNotEmpty() })

            // Set artwork if available
            if (artworkPath.isNotEmpty()) {
                try {
                    val artworkUri = android.net.Uri.parse(artworkPath)
                    if (artworkUri.scheme != null) {
                        metadataBuilder.setArtworkUri(artworkUri)
                    } else {
                        // Local file path
                        val artworkFile = File(artworkPath)
                        if (artworkFile.exists()) {
                            metadataBuilder.setArtworkUri(android.net.Uri.fromFile(artworkFile))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AudioPlayerService", "Failed to parse artwork path: $artworkPath", e)
                }
            }

            if (extras != null) {
                metadataBuilder.setExtras(extras)
            }

            // Create MediaItem from file path
            val file = File(filePath)
            if (!file.exists()) {
                android.util.Log.w("AudioPlayerService", "Stored file does not exist: $filePath")
                throw IllegalStateException("stored file does not exist")
            }

            val uri = android.net.Uri.fromFile(file)
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(uri)
                    .setMediaId(filePath) // Use file path as media ID
                    .setMediaMetadata(metadataBuilder.build())
                    .build()

            // Return MediaItemsWithStartPosition with single item and saved position
            MediaSession.MediaItemsWithStartPosition(
                listOf(mediaItem),
                0,
                positionMs,
            )
        }
}
