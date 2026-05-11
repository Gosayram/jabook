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

import com.jabook.app.jabook.util.LogUtils

/**
 * Bootstraps runtime initialization for [AudioPlayerService].
 *
 * Keeps `AudioPlayerService.onCreate()` as thin orchestration while preserving
 * exact initialization order and fallback behavior.
 */
internal object AudioPlayerServiceBootstrapper {
    fun initialize(service: AudioPlayerService) {
        val helper = NotificationHelper(service)
        service.notificationHelper = helper

        val initialNotification =
            try {
                helper.createMinimalNotification()
            } catch (e: Exception) {
                LogUtils.w("AudioPlayerService", "Failed to create minimal notification, using fallback", e)
                helper.createFallbackNotification()
            }

        val foregroundStartResult =
            service.foregroundNotificationCoordinator.startWithFallback(
                service = service,
                notificationId = NotificationHelper.NOTIFICATION_ID,
                primaryNotification = initialNotification,
                fallbackNotificationProvider = { helper.createFallbackNotification() },
                event = "service_on_create",
            )
        if (foregroundStartResult == ForegroundStartResult.FAILED) {
            LogUtils.e("AudioPlayerService", "Failed to start foreground with both notifications")
        } else {
            LogUtils.d("AudioPlayerService", "startForeground() completed: $foregroundStartResult")
        }

        service.attachMediaSessionServiceListener()
        PlayerPerformanceLogger.log("Service", "listener set")

        AudioPlayerServiceInitializer(service).let { initializer ->
            initializer.initialize()
            initializer.postInitialize()
        }
        PlayerPerformanceLogger.log("Service", "initialization complete")
    }
}
