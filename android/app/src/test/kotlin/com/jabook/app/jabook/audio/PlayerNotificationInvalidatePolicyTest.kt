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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNotificationInvalidatePolicyTest {
    @Test
    fun `first debounced signal schedules invalidate`() {
        val policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 123L)

        val action = policy.onDebouncedSignal()

        assertEquals(PlayerNotificationInvalidatePolicy.DebouncedAction.SCHEDULE, action)
        assertEquals(123L, policy.debounceDelayMs())
    }

    @Test
    fun `repeated debounced signal is coalesced while pending`() {
        val policy = PlayerNotificationInvalidatePolicy()

        val first = policy.onDebouncedSignal()
        val second = policy.onDebouncedSignal()

        assertEquals(PlayerNotificationInvalidatePolicy.DebouncedAction.SCHEDULE, first)
        assertEquals(PlayerNotificationInvalidatePolicy.DebouncedAction.COALESCED, second)
    }

    @Test
    fun `debounced delivery resets pending state`() {
        val policy = PlayerNotificationInvalidatePolicy()
        policy.onDebouncedSignal()
        policy.onDebouncedInvalidateDelivered()

        val next = policy.onDebouncedSignal()

        assertEquals(PlayerNotificationInvalidatePolicy.DebouncedAction.SCHEDULE, next)
    }

    @Test
    fun `immediate signal requests cancel when debounced update is pending`() {
        val policy = PlayerNotificationInvalidatePolicy()
        policy.onDebouncedSignal()

        val immediateAction = policy.onImmediateSignal()
        val nextDebounced = policy.onDebouncedSignal()

        assertTrue(immediateAction.cancelPendingDebounced)
        assertEquals(PlayerNotificationInvalidatePolicy.DebouncedAction.SCHEDULE, nextDebounced)
    }

    @Test
    fun `immediate signal does not request cancel when no pending debounced update`() {
        val policy = PlayerNotificationInvalidatePolicy()

        val immediateAction = policy.onImmediateSignal()

        assertFalse(immediateAction.cancelPendingDebounced)
    }

    @Test
    fun `debounced cancellation resets pending state`() {
        val policy = PlayerNotificationInvalidatePolicy()
        policy.onDebouncedSignal()
        policy.onDebouncedInvalidateCancelled()

        val next = policy.onDebouncedSignal()

        assertEquals(PlayerNotificationInvalidatePolicy.DebouncedAction.SCHEDULE, next)
    }
}
