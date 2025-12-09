package com.jabook.app.jabook.audio

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
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
import kotlinx.coroutines.guava.future
import java.io.File

class AudioPlayerLibrarySessionCallback(
    private val service: AudioPlayerService,
    private val playerPersistenceManager: PlayerPersistenceManager,
    private val getDurationForFile: (String) -> Long?,
) : MediaLibraryService.MediaLibrarySession.Callback {
    // TODO: Implement library operations (onGetLibraryRoot, onGetItem, onGetChildren)
    // For now, these are not needed as we're not using library browsing features

    private val customCommands =
        listOf(
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName("Rewind 15s")
                .setIconResId(android.R.drawable.ic_media_rew)
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(CUSTOM_COMMAND_REWIND, Bundle.EMPTY),
                ).build(),
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName("Forward 30s")
                .setIconResId(android.R.drawable.ic_media_ff)
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(CUSTOM_COMMAND_FORWARD, Bundle.EMPTY),
                ).build(),
        )

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val connectionResult =
            MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_REWIND, Bundle.EMPTY))
                        .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_FORWARD, Bundle.EMPTY))
                        .build(),
                ).setMediaButtonPreferences(customCommands)
                .build()

        return connectionResult
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            CUSTOM_COMMAND_REWIND -> {
                val rewindSeconds = service.mediaSessionManager?.getRewindDuration()?.toInt() ?: 15
                service.rewind(rewindSeconds)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_FORWARD -> {
                val forwardSeconds = service.mediaSessionManager?.getForwardDuration()?.toInt() ?: 30
                service.forward(forwardSeconds)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    companion object {
        private const val CUSTOM_COMMAND_REWIND = "com.jabook.app.jabook.rewind"
        private const val CUSTOM_COMMAND_FORWARD = "com.jabook.app.jabook.forward"
    }

    // Minimal implementation for library operations (required by MediaLibrarySession.Callback)
    // These are not used in our case, but must be implemented
    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        CoroutineScope(Dispatchers.IO).future {
            val rootItem =
                MediaItem
                    .Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setTitle("Library")
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build(),
                    ).build()
            LibraryResult.ofItem(rootItem, params)
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
                                .setTitle("Library")
                                .setIsBrowsable(true)
                                .build(),
                        ).build()
                return@future LibraryResult.ofItem(rootItem, null)
            }

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
                if (persistedState.filePaths.contains(mediaId)) {
                    // Return the file item
                    val file = File(mediaId)
                    val item =
                        MediaItem
                            .Builder()
                            .setMediaId(mediaId)
                            .setUri(android.net.Uri.fromFile(file))
                            .setMediaMetadata(
                                MediaMetadata
                                    .Builder()
                                    .setTitle(file.nameWithoutExtension)
                                    .setIsBrowsable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build(),
                            ).build()
                    return@future LibraryResult.ofItem(item, null)
                }
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

            if (parentId == "root") {
                // Return "Last Played" book
                if (persistedState != null) {
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

                    items.add(
                        MediaItem
                            .Builder()
                            .setMediaId(persistedState.groupPath)
                            .setMediaMetadata(metadataBuilder.build())
                            .build(),
                    )
                }
            } else if (persistedState != null && parentId == persistedState.groupPath) {
                // Return chapters
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

                                android.util.Log.d("AudioPlayerService", "Added completion extras: ${completionPercentage * 100}%")
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
                        android.util.Log.w("AudioPlayerService", "Skipping missing file in restored playlist: $filePath")
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

                    android.util.Log.i("AudioPlayerService", "Restoring full playlist: ${playlist.size} items, index=$correctedIndex")

                    return@future MediaSession.MediaItemsWithStartPosition(
                        playlist,
                        correctedIndex,
                        persistedState.currentPosition,
                    )
                }
            }

            val storedData = playerPersistenceManager.retrieveLastStoredMediaItem()
            if (storedData == null) {
                android.util.Log.w("AudioPlayerService", "No stored media item found for playback resumption")
                throw IllegalStateException("previous media id not found")
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
                val completionPercentage = kotlin.math.max(0.0, kotlin.math.min(1.0, positionMs.toDouble() / durationMs))
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
