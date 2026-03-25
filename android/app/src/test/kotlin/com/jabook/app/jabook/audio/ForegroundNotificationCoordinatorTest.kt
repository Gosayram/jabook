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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class ForegroundNotificationCoordinatorTest {
    @Test
    fun `primary start succeeds without fallback`() {
        val primary: Notification = mock()
        val fallback: Notification = mock()
        val startedNotifications = mutableListOf<Notification>()
        var fallbackBuilt = false

        val coordinator =
            ForegroundNotificationCoordinator(
                startForegroundCall = { _, notification ->
                    startedNotifications += notification
                },
            )

        val result =
            coordinator.startWithFallback(
                notificationId = 1,
                primaryNotification = primary,
                fallbackNotificationProvider = {
                    fallbackBuilt = true
                    fallback
                },
                event = "test_primary",
            )

        assertEquals(ForegroundStartResult.PRIMARY_STARTED, result)
        assertEquals(listOf(primary), startedNotifications)
        assertFalse(fallbackBuilt)
    }

    @Test
    fun `fallback start is used when primary fails`() {
        val primary: Notification = mock()
        val fallback: Notification = mock()
        val startedNotifications = mutableListOf<Notification>()
        var startCallCount = 0
        var fallbackBuilt = false

        val coordinator =
            ForegroundNotificationCoordinator(
                startForegroundCall = { _, notification ->
                    startCallCount += 1
                    if (startCallCount == 1) {
                        throw IllegalStateException("primary failed")
                    }
                    startedNotifications += notification
                },
            )

        val result =
            coordinator.startWithFallback(
                notificationId = 2,
                primaryNotification = primary,
                fallbackNotificationProvider = {
                    fallbackBuilt = true
                    fallback
                },
                event = "test_fallback",
            )

        assertEquals(ForegroundStartResult.FALLBACK_STARTED, result)
        assertTrue(fallbackBuilt)
        assertEquals(listOf(fallback), startedNotifications)
    }

    @Test
    fun `result is failed when both primary and fallback start fail`() {
        val primary: Notification = mock()
        val fallback: Notification = mock()
        var startCallCount = 0

        val coordinator =
            ForegroundNotificationCoordinator(
                startForegroundCall = { _, _ ->
                    startCallCount += 1
                    throw IllegalStateException("start failed")
                },
            )

        val result =
            coordinator.startWithFallback(
                notificationId = 3,
                primaryNotification = primary,
                fallbackNotificationProvider = { fallback },
                event = "test_failed",
            )

        assertEquals(ForegroundStartResult.FAILED, result)
        assertEquals(2, startCallCount)
    }

    @Test
    fun `result is failed when fallback notification creation fails`() {
        val primary: Notification = mock()
        var startCallCount = 0

        val coordinator =
            ForegroundNotificationCoordinator(
                startForegroundCall = { _, _ ->
                    startCallCount += 1
                    throw IllegalStateException("primary failed")
                },
            )

        val result =
            coordinator.startWithFallback(
                notificationId = 4,
                primaryNotification = primary,
                fallbackNotificationProvider = { throw IllegalArgumentException("fallback build failed") },
                event = "test_fallback_build_failed",
            )

        assertEquals(ForegroundStartResult.FAILED, result)
        assertEquals(1, startCallCount)
    }

    @Test
    fun `repeated promote inside debounce window is skipped`() {
        var nowMs = 1_000L
        val primary: Notification = mock()
        val startedNotifications = mutableListOf<Notification>()
        val repromotePolicy =
            ForegroundRepromotePolicy(
                nowMsProvider = { nowMs },
                minIntervalMs = 500L,
            )

        val coordinator =
            ForegroundNotificationCoordinator(
                startForegroundCall = { _, notification ->
                    startedNotifications += notification
                },
                repromotePolicy = repromotePolicy,
            )

        val first =
            coordinator.startWithFallback(
                notificationId = 5,
                primaryNotification = primary,
                fallbackNotificationProvider = { mock() },
                event = "first",
            )
        nowMs += 100L
        val second =
            coordinator.startWithFallback(
                notificationId = 5,
                primaryNotification = primary,
                fallbackNotificationProvider = { mock() },
                event = "second",
            )

        assertEquals(ForegroundStartResult.PRIMARY_STARTED, first)
        assertEquals(ForegroundStartResult.SKIPPED, second)
        assertEquals(1, startedNotifications.size)
    }

    @Test
    fun `failed promote does not block immediate retry`() {
        val primary: Notification = mock()
        val fallback: Notification = mock()
        var startCallCount = 0

        val coordinator =
            ForegroundNotificationCoordinator(
                startForegroundCall = { _, notification ->
                    startCallCount += 1
                    if (startCallCount == 1 && notification == primary) {
                        throw IllegalStateException("first primary failed")
                    }
                },
                repromotePolicy =
                    ForegroundRepromotePolicy(
                        nowMsProvider = { 1_000L },
                        minIntervalMs = 5_000L,
                    ),
            )

        val first =
            coordinator.startWithFallback(
                notificationId = 6,
                primaryNotification = primary,
                fallbackNotificationProvider = { throw IllegalStateException("fallback failed") },
                event = "first_failed",
            )
        val retry =
            coordinator.startWithFallback(
                notificationId = 6,
                primaryNotification = primary,
                fallbackNotificationProvider = { fallback },
                event = "retry",
            )

        assertEquals(ForegroundStartResult.FAILED, first)
        assertEquals(ForegroundStartResult.PRIMARY_STARTED, retry)
        assertEquals(2, startCallCount)
    }
}
