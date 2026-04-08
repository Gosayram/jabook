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
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Result of a foreground service start attempt.
 *
 * Encodes whether the call succeeded, was denied by the system, or failed
 * for an unexpected reason. Callers decide how to react (retry, stop, etc.).
 */
public enum class ForegroundStartOutcome {
    /** `startForeground` completed successfully. */
    SUCCESS,

    /**
     * The system denied the call because the app is not allowed to start
     * a foreground service of the requested type right now (Android 14+).
     *
     * Typical causes:
     * - Missing `FOREGROUND_SERVICE_*` permission for the declared type.
     * - The app is in a background state that does not qualify for the type.
     */
    DENIED_BY_SYSTEM,

    /** An unexpected exception was thrown (not a permission/system denial). */
    FAILED,
}

/**
 * Centralizes the Android-version-aware `startForeground` call for all
 * foreground services in the app.
 *
 * Starting with Android 14 (API 34) the two-argument
 * `startForeground(id, notification)` is deprecated and the system requires
 * the three-argument overload that carries an explicit `foregroundServiceType`.
 * Additionally, a [SecurityException] is thrown when the app does not hold the
 * type-specific permission (e.g. `FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
 *
 * This policy:
 * - Calls the correct overload based on `Build.VERSION.SDK_INT`.
 * - Catches [SecurityException] on API 34+ and maps it to [ForegroundStartOutcome.DENIED_BY_SYSTEM].
 * - Catches any other [Exception] and maps it to [ForegroundStartOutcome.FAILED].
 */
public class ForegroundServiceStartPolicy(
    private val logDebug: (String) -> Unit = {},
    private val logWarn: (String, Throwable?) -> Unit = { _, _ -> },
) {
    /**
     * Starts the given [service] in the foreground with the Android-version-
     * appropriate overload.
     *
     * @param service        The foreground service instance.
     * @param notificationId Stable notification ID for this service.
     * @param notification   The foreground notification to display.
     * @param serviceType    The `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` constant
     *                       that matches both the manifest declaration and the
     *                       type-specific permission held by the app.
     * @param event          Human-readable label for log messages (e.g. "onCreate").
     * @return The outcome of the start attempt.
     */
    public fun startForeground(
        service: Service,
        notificationId: Int,
        notification: Notification,
        serviceType: Int,
        event: String,
    ): ForegroundStartOutcome =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForegroundApi34(service, notificationId, notification, serviceType)
            } else {
                @Suppress("DEPRECATION")
                service.startForeground(notificationId, notification)
            }
            logDebug("foreground_start_success event=$event notificationId=$notificationId api=${Build.VERSION.SDK_INT}")
            ForegroundStartOutcome.SUCCESS
        } catch (e: SecurityException) {
            logWarn(
                "foreground_start_denied event=$event notificationId=$notificationId " +
                    "type=$serviceType api=${Build.VERSION.SDK_INT}",
                e,
            )
            ForegroundStartOutcome.DENIED_BY_SYSTEM
        } catch (e: Exception) {
            logWarn(
                "foreground_start_failed event=$event notificationId=$notificationId " +
                    "api=${Build.VERSION.SDK_INT}",
                e,
            )
            ForegroundStartOutcome.FAILED
        }

    /**
     * Checks whether the current runtime requires the three-argument
     * `startForeground` overload (API 34+).
     */
    public fun requiresTypedForegroundStart(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startForegroundApi34(
        service: Service,
        notificationId: Int,
        notification: Notification,
        serviceType: Int,
    ) {
        service.startForeground(notificationId, notification, serviceType)
    }
}
