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
    // --- onPress ---

    @Test
    fun `onPress saves current speed and returns boost speed`() {
        val policy = HoldToBoostPolicy(boostSpeed = 3.0f)
        val result = policy.onPress(1.5f)

        assertEquals(3.0f, result, 0.001f)
        assertTrue(policy.isBoosting)
    }

    @Test
    fun `onPress when already boosting returns boost speed`() {
        val policy = HoldToBoostPolicy(boostSpeed = 3.0f)
        policy.onPress(1.0f)
        val result = policy.onPress(2.0f)

        assertEquals(3.0f, result, 0.001f)
    }

    // --- onRelease ---

    @Test
    fun `onRelease restores saved speed`() {
        val policy = HoldToBoostPolicy(boostSpeed = 3.0f)
        policy.onPress(1.5f)
        val restored = policy.onRelease()

        assertEquals(1.5f, restored!!, 0.001f)
        assertFalse(policy.isBoosting)
    }

    @Test
    fun `onRelease without press returns null`() {
        val policy = HoldToBoostPolicy()
        val result = policy.onRelease()

        assertEquals(null, result)
    }

    // --- onCancel ---

    @Test
    fun `onCancel restores saved speed`() {
        val policy = HoldToBoostPolicy(boostSpeed = 3.0f)
        policy.onPress(2.0f)
        val restored = policy.onCancel()

        assertEquals(2.0f, restored!!, 0.001f)
        assertFalse(policy.isBoosting)
    }

    // --- init validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `negative boost speed throws`() {
        HoldToBoostPolicy(boostSpeed = -1.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero boost speed throws`() {
        HoldToBoostPolicy(boostSpeed = 0.0f)
    }

    // --- DEFAULT_BOOST_SPEED ---

    @Test
    fun `default boost speed is 3x`() {
        assertEquals(3.0f, HoldToBoostPolicy.DEFAULT_BOOST_SPEED, 0.001f)
    }
}
