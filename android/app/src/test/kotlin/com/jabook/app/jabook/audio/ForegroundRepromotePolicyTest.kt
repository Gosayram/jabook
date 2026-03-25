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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRepromotePolicyTest {
    @Test
    fun `first attempt is always allowed`() {
        val policy =
            ForegroundRepromotePolicy(
                nowMsProvider = { 1_000L },
                minIntervalMs = 500L,
            )

        assertTrue(policy.shouldAttempt(notificationId = 1))
    }

    @Test
    fun `attempt is blocked inside minimum interval`() {
        var nowMs = 1_000L
        val policy =
            ForegroundRepromotePolicy(
                nowMsProvider = { nowMs },
                minIntervalMs = 500L,
            )

        assertTrue(policy.shouldAttempt(notificationId = 1))
        policy.onPromotionSucceeded(notificationId = 1)

        nowMs += 100L
        assertFalse(policy.shouldAttempt(notificationId = 1))
    }

    @Test
    fun `attempt is allowed again after minimum interval`() {
        var nowMs = 1_000L
        val policy =
            ForegroundRepromotePolicy(
                nowMsProvider = { nowMs },
                minIntervalMs = 500L,
            )

        assertTrue(policy.shouldAttempt(notificationId = 1))
        policy.onPromotionSucceeded(notificationId = 1)

        nowMs += 500L
        assertTrue(policy.shouldAttempt(notificationId = 1))
    }

    @Test
    fun `clock rollback does not permanently block attempts`() {
        var nowMs = 1_000L
        val policy =
            ForegroundRepromotePolicy(
                nowMsProvider = { nowMs },
                minIntervalMs = 500L,
            )

        assertTrue(policy.shouldAttempt(notificationId = 1))
        policy.onPromotionSucceeded(notificationId = 1)

        nowMs = 900L
        assertTrue(policy.shouldAttempt(notificationId = 1))
    }
}
