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

import android.app.Notification

internal enum class ForegroundStartResult {
    PRIMARY_STARTED,
    FALLBACK_STARTED,
    SKIPPED,
    FAILED,
}

/**
 * Centralizes foreground notification promotion with a single fallback attempt.
 */
internal class ForegroundNotificationCoordinator(
    private val startForegroundCall: (Int, Notification) -> Unit,
    private val repromotePolicy: ForegroundRepromotePolicy = ForegroundRepromotePolicy(),
    private val logDebug: (String) -> Unit = {},
    private val logWarn: (String, Throwable?) -> Unit = { _, _ -> },
) {
    internal fun startWithFallback(
        notificationId: Int,
        primaryNotification: Notification,
        fallbackNotificationProvider: () -> Notification,
        event: String,
    ): ForegroundStartResult {
        if (!repromotePolicy.shouldAttempt(notificationId)) {
            logDebug("foreground_start_skipped event=$event reason=debounced notificationId=$notificationId")
            return ForegroundStartResult.SKIPPED
        }

        return try {
            startForegroundCall(notificationId, primaryNotification)
            repromotePolicy.onPromotionSucceeded(notificationId)
            logDebug("foreground_start_success event=$event path=primary notificationId=$notificationId")
            ForegroundStartResult.PRIMARY_STARTED
        } catch (primaryError: Exception) {
            logWarn(
                "foreground_start_failure event=$event path=primary notificationId=$notificationId",
                primaryError,
            )

            val fallbackNotification =
                try {
                    fallbackNotificationProvider()
                } catch (fallbackBuildError: Exception) {
                    logWarn(
                        "foreground_start_failure event=$event path=fallback_build notificationId=$notificationId",
                        fallbackBuildError,
                    )
                    return ForegroundStartResult.FAILED
                }

            try {
                startForegroundCall(notificationId, fallbackNotification)
                repromotePolicy.onPromotionSucceeded(notificationId)
                logWarn("foreground_start_success event=$event path=fallback notificationId=$notificationId", null)
                ForegroundStartResult.FALLBACK_STARTED
            } catch (fallbackError: Exception) {
                logWarn(
                    "foreground_start_failure event=$event path=fallback notificationId=$notificationId",
                    fallbackError,
                )
                ForegroundStartResult.FAILED
            }
        }
    }
}
