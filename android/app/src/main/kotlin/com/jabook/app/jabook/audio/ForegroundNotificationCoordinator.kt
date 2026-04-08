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
import android.app.Service
import android.content.pm.ServiceInfo

/**
 * Centralizes foreground notification promotion with a single fallback attempt,
 * using the Android 14+ aware [ForegroundServiceStartPolicy].
 *
 * On API 34+ the three-argument `startForeground(id, notification, type)` is
 * called; on older platforms the two-argument overload is used instead.
 * [SecurityException] thrown on API 34+ is mapped to
 * [ForegroundStartResult.DENIED_BY_SYSTEM] so callers can react gracefully
 * (e.g. stop the service).
 */
internal class ForegroundNotificationCoordinator(
    private val policy: ForegroundServiceStartPolicy,
    private val serviceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
    private val repromotePolicy: ForegroundRepromotePolicy = ForegroundRepromotePolicy(),
) {
    /**
     * @suppress
     * Binary-compatible bridge for call-sites that still pass lambdas.
     * Prefer the primary constructor that accepts [ForegroundServiceStartPolicy].
     */
    internal constructor(
        startForegroundCall: (Int, Notification) -> Unit,
        repromotePolicy: ForegroundRepromotePolicy = ForegroundRepromotePolicy(),
        logDebug: (String) -> Unit = {},
        logWarn: (String, Throwable?) -> Unit = { _, _ -> },
    ) : this(
        policy = ForegroundServiceStartPolicy(logDebug, logWarn),
        repromotePolicy = repromotePolicy,
    ) {
        legacyStartForeground = startForegroundCall
    }

    private var legacyStartForeground: ((Int, Notification) -> Unit)? = null

    /**
     * Attempts to promote the given [service] to foreground with a primary
     * notification and a single fallback attempt if the first call fails.
     *
     * @param service                   The foreground service instance.
     * @param notificationId            Stable notification ID.
     * @param primaryNotification       The preferred notification.
     * @param fallbackNotificationProvider Builds a simpler notification for the retry path.
     * @param event                     Human-readable label for log messages.
     * @return The [ForegroundStartResult] describing what happened.
     */
    internal fun startWithFallback(
        service: Service,
        notificationId: Int,
        primaryNotification: Notification,
        fallbackNotificationProvider: () -> Notification,
        event: String,
    ): ForegroundStartResult {
        if (!repromotePolicy.shouldAttempt(notificationId)) {
            return ForegroundStartResult.SKIPPED
        }

        // Primary attempt
        val primaryOutcome =
            policy.startForeground(
                service,
                notificationId,
                primaryNotification,
                serviceType,
                "$event-primary",
            )
        when (primaryOutcome) {
            ForegroundStartOutcome.SUCCESS -> {
                repromotePolicy.onPromotionSucceeded(notificationId)
                return ForegroundStartResult.PRIMARY_STARTED
            }
            ForegroundStartOutcome.DENIED_BY_SYSTEM -> {
                return ForegroundStartResult.DENIED_BY_SYSTEM
            }
            ForegroundStartOutcome.FAILED -> { /* fall through to fallback */ }
        }

        // Fallback attempt with simpler notification
        val fallbackNotification =
            try {
                fallbackNotificationProvider()
            } catch (e: Exception) {
                return ForegroundStartResult.FAILED
            }

        val fallbackOutcome =
            policy.startForeground(
                service,
                notificationId,
                fallbackNotification,
                serviceType,
                "$event-fallback",
            )
        return when (fallbackOutcome) {
            ForegroundStartOutcome.SUCCESS -> {
                repromotePolicy.onPromotionSucceeded(notificationId)
                ForegroundStartResult.FALLBACK_STARTED
            }
            ForegroundStartOutcome.DENIED_BY_SYSTEM -> ForegroundStartResult.DENIED_BY_SYSTEM
            ForegroundStartOutcome.FAILED -> ForegroundStartResult.FAILED
        }
    }

    /**
     * @suppress
     * Legacy overload for call-sites that have not been migrated to pass [Service].
     * Uses the deprecated lambda-based approach without API 34+ type support.
     */
    internal fun startWithFallback(
        notificationId: Int,
        primaryNotification: Notification,
        fallbackNotificationProvider: () -> Notification,
        event: String,
    ): ForegroundStartResult {
        if (!repromotePolicy.shouldAttempt(notificationId)) {
            return ForegroundStartResult.SKIPPED
        }

        val startCall = legacyStartForeground ?: return ForegroundStartResult.FAILED

        return try {
            startCall(notificationId, primaryNotification)
            repromotePolicy.onPromotionSucceeded(notificationId)
            ForegroundStartResult.PRIMARY_STARTED
        } catch (_: Exception) {
            val fallbackNotification =
                try {
                    fallbackNotificationProvider()
                } catch (_: Exception) {
                    return ForegroundStartResult.FAILED
                }

            try {
                startCall(notificationId, fallbackNotification)
                repromotePolicy.onPromotionSucceeded(notificationId)
                ForegroundStartResult.FALLBACK_STARTED
            } catch (_: Exception) {
                ForegroundStartResult.FAILED
            }
        }
    }
}

/**
 * Result of a foreground service start attempt via [ForegroundNotificationCoordinator].
 */
internal enum class ForegroundStartResult {
    /** The primary notification was accepted. */
    PRIMARY_STARTED,

    /** The fallback notification was accepted. */
    FALLBACK_STARTED,

    /** The attempt was skipped (debounce). */
    SKIPPED,

    /** The system denied the foreground service start (Android 14+). */
    DENIED_BY_SYSTEM,

    /** An unexpected failure occurred. */
    FAILED,
}
