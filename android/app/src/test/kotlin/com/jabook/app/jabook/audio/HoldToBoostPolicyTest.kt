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

class HoldToBoostPolicyTest {
    private val policy = HoldToBoostPolicy(boostSpeed = 3.0f)

    @Test
    fun `initial state is not boosting`() {
        assertFalse(policy.isBoosting)
    }

    @Test
    fun `onPress activates boost and returns boost speed`() {
        val speed = policy.onPress(1.5f)
        assertEquals(3.0f, speed, 0.01f)
        assertTrue(policy.isBoosting)
    }

    @Test
    fun `onRelease restores previous speed`() {
        policy.onPress(1.5f)
        val speed = policy.onRelease()
        assertEquals(1.5f, speed!!, 0.01f)
        assertFalse(policy.isBoosting)
    }

    @Test
    fun `onCancel restores previous speed`() {
        policy.onPress(2.0f)
        val speed = policy.onCancel()
        assertEquals(2.0f, speed!!, 0.01f)
        assertFalse(policy.isBoosting)
    }

    @Test
    fun `onRelease without press returns null`() {
        val speed = policy.onRelease()
        assertEquals(null, speed)
    }

    @Test
    fun `duplicate onPress ignored without affecting saved speed`() {
        policy.onPress(1.2f)
        val speed = policy.onPress(2.5f) // duplicate press
        assertEquals(3.0f, speed, 0.01f) // still returns boost speed

        val restored = policy.onRelease()
        assertEquals(1.2f, restored!!, 0.01f) // restores original, not second press
    }

    @Test
    fun `duplicate onRelease returns null after first release`() {
        policy.onPress(1.5f)
        val firstRelease = policy.onRelease()
        assertEquals(1.5f, firstRelease!!, 0.01f)

        val secondRelease = policy.onRelease()
        assertEquals(null, secondRelease) // no saved speed
    }

    @Test
    fun `duplicate onCancel returns null after first cancel`() {
        policy.onPress(2.0f)
        val firstCancel = policy.onCancel()
        assertEquals(2.0f, firstCancel!!, 0.01f)

        val secondCancel = policy.onCancel()
        assertEquals(null, secondCancel) // no saved speed
    }

    @Test
    fun `currentSpeed reflects boost state`() {
        assertEquals(null, policy.currentSpeed) // not boosting, no saved speed
        policy.onPress(1.5f)
        assertEquals(3.0f, policy.currentSpeed!!, 0.01f) // boosting
        policy.onRelease()
        assertEquals(null, policy.currentSpeed) // released
    }

    @Test
    fun `full lifecycle press-release-re-press works`() {
        // First cycle: press at 1x, boost, release
        val boostSpeed = policy.onPress(1.0f)
        assertEquals(3.0f, boostSpeed, 0.01f)
        assertEquals(1.0f, policy.onRelease()!!, 0.01f)
        assertFalse(policy.isBoosting)

        // Second cycle with different base speed
        val boostSpeed2 = policy.onPress(2.0f)
        assertEquals(3.0f, boostSpeed2, 0.01f)
        assertEquals(3.0f, policy.currentSpeed!!, 0.01f)
        assertEquals(2.0f, policy.onRelease()!!, 0.01f)
        assertFalse(policy.isBoosting)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative boost speed throws`() {
        HoldToBoostPolicy(boostSpeed = -1.0f)
    }

    @Test
    fun `custom boost speed`() {
        val custom = HoldToBoostPolicy(boostSpeed = 4.0f)
        val speed = custom.onPress(1.0f)
        assertEquals(4.0f, speed, 0.01f)
    }
}
