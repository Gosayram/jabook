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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class HeadsetAutoplayHandlerTest {
    private lateinit var autoplayCount: Int
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        autoplayCount = 0
        testScope = TestScope(StandardTestDispatcher())
    }

    private fun createHandler(connected: Boolean = true): HeadsetAutoplayHandler =
        HeadsetAutoplayHandler(
            isDeviceConnected = { connected },
        )

    // --- Wired headset triggers immediately ---

    @Test
    fun `wired headset triggers immediately`() {
        val device = mockDevice(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET)
        val handler = createHandler()

        handler.onHeadsetConnected(device, testScope) { autoplayCount++ }
        assertEquals(1, autoplayCount)
    }

    @Test
    fun `wired headphones triggers immediately`() {
        val device = mockDevice(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        val handler = createHandler()

        handler.onHeadsetConnected(device, testScope) { autoplayCount++ }
        assertEquals(1, autoplayCount)
    }

    // --- Bluetooth delays ---

    @Test
    fun `bluetooth A2DP delays autoplay`() =
        testScope.runTest {
            val device = mockDevice(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            val handler = createHandler(connected = true)

            handler.onHeadsetConnected(device, testScope) { autoplayCount++ }
            assertEquals("Should not play immediately", 0, autoplayCount)

            advanceUntilIdle()
            assertEquals("Should play after delay", 1, autoplayCount)
        }

    @Test
    fun `bluetooth does not autoplay if device disconnects during delay`() =
        testScope.runTest {
            val device = mockDevice(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            val handler = createHandler(connected = false)

            handler.onHeadsetConnected(device, testScope) { autoplayCount++ }
            advanceUntilIdle()

            assertEquals("Should not play if device disconnected", 0, autoplayCount)
        }

    private fun mockDevice(type: Int): android.media.AudioDeviceInfo {
        val device = mock<android.media.AudioDeviceInfo>()
        org.mockito.kotlin
            .whenever(device.type)
            .thenReturn(type)
        return device
    }
}
