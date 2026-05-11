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

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit

/**
 * Creates and wires Media3 session/controller objects for [AudioPlayerService].
 */
internal class AudioSessionSetup(
    private val service: AudioPlayerService,
) {
    @OptIn(UnstableApi::class)
    fun initializeMediaSession() {
        if (service.mediaLibrarySession != null) return

        try {
            val sessionActivity = service.getBackStackedActivity() ?: service.getSingleTopActivity()

            val callback =
                AudioPlayerLibrarySessionCallback(
                    service,
                    service.playerPersistenceManager,
                    service.torrentDownloadRepository,
                    service.mediaButtonHandler,
                    { filePath -> service.durationManager.getDurationForFile(filePath) },
                )

            val sessionId = "jabook_${android.os.Process.myPid()}_${System.identityHashCode(service)}"
            android.util.Log.i("AudioPlayerService", "Creating MediaLibrarySession with ID: $sessionId")

            val sessionBuilder =
                MediaLibrarySession
                    .Builder(service, service.exoPlayer, callback)
                    .setId(sessionId)

            if (sessionActivity != null) {
                sessionBuilder.setSessionActivity(sessionActivity)
            } else {
                android.util.Log.w("AudioPlayerService", "Session activity intent is null")
            }

            service.mediaLibrarySession = sessionBuilder.build()
            service.mediaLibrarySession?.sessionExtras =
                Bundle().apply {
                    putBoolean(androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
                    putBoolean(androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
                }
            service.mediaSession = service.mediaLibrarySession

            android.util.Log.i(
                "AudioPlayerService",
                "MediaLibrarySession created successfully: ${service.mediaLibrarySession?.token}",
            )

            createServiceMediaController()
            service.mediaSessionManager = MediaSessionManager(service, service.exoPlayer)
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to create MediaLibrarySession", e)
        }
    }

    @OptIn(UnstableApi::class)
    private fun createServiceMediaController() {
        val session = service.mediaLibrarySession
        if (session == null) {
            android.util.Log.w("AudioPlayerService", "Cannot create MediaController: MediaLibrarySession is null")
            return
        }

        try {
            val controllerFuture: ListenableFuture<MediaController> =
                MediaController
                    .Builder(service, session.token)
                    .setApplicationLooper(service.mainLooper)
                    .buildAsync()

            controllerFuture.addListener(
                {
                    try {
                        val controller =
                            controllerFuture.get(
                                MediaControllerConstants.SERVICE_INIT_TIMEOUT_SECONDS.toLong(),
                                TimeUnit.SECONDS,
                            )
                        service.serviceMediaController = controller
                        service.isFullyInitializedFlag = true
                        service.setInitialCustomLayout()
                        android.util.Log.i(
                            "AudioPlayerService",
                            "Service MediaController initialized successfully, service is now fully ready",
                        )
                    } catch (e: java.util.concurrent.TimeoutException) {
                        android.util.Log.e("AudioPlayerService", "Service MediaController initialization timeout", e)
                        service.isFullyInitializedFlag = true
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Error initializing Service MediaController", e)
                        service.isFullyInitializedFlag = true
                    }
                },
                ContextCompat.getMainExecutor(service),
            )
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to create Service MediaController", e)
        }
    }
}
