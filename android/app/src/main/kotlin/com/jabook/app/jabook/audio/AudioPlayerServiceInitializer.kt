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

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Handles initialization logic for AudioPlayerService.
 * Extracts complex initialization code from onCreate to improve readability.
 */
public class AudioPlayerServiceInitializer(
    private val service: AudioPlayerService,
) {
    @OptIn(UnstableApi::class)
    public fun initialize() {
        AudioCrossFadeSessionBinder.bind(service)
        android.util.Log.i("AudioPlayerService", "Initializing service components...")

        // NOTE: NotificationHelper is already initialized in onCreate() for immediate startForeground()
        // Only initialize if not already set (for safety)
        if (service.notificationHelper == null) {
            service.notificationHelper = NotificationHelper(service)
        }

        AudioServiceComponentBinder.bind(service)

        // Initialize MediaSession (Media3)
        AudioSessionSetup(service).initializeMediaSession()

        // Note: isFullyInitializedFlag will be set after MediaController is created
        // This ensures service is truly ready before components try to use it

        // Start settings synchronization to MediaSession
        // This ensures system media controls always reflect current app settings
        MediaSessionSettingsSyncInitializer.initialize(service)

        android.util.Log.i("AudioPlayerService", "Service components initialized successfully")
    }

    /**
     * Post-initialization setup called after initialize().
     * Handles: playback speed restore, notification provider, audio output, visualizer, enhancer.
     */
    public fun postInitialize() {
        AudioPlayerPostInitCoordinator(service).run()
    }
}
