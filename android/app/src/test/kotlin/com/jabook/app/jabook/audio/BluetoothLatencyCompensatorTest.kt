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

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BluetoothLatencyCompensatorTest {
    private lateinit var audioManager: android.media.AudioManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        audioManager = mock()
        context = mock()
        whenever(audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))
            .thenReturn(emptyArray())
    }

    private fun createCompensator(): BluetoothLatencyCompensator = BluetoothLatencyCompensator(audioManager, context)

    // --- Non-BT returns zero latency ---

    @Test
    fun `non-Bluetooth output returns zero latency`() {
        val compensator = createCompensator()
        assertEquals(0L, compensator.getEstimatedLatencyMs())
    }

    // --- compensatePosition with no BT is identity ---

    @Test
    fun `compensatePosition with no Bluetooth is identity`() {
        val compensator = createCompensator()
        assertEquals(50_000L, compensator.compensatePosition(50_000L))
    }

    // --- BT A2DP active returns non-zero latency ---

    @Test
    fun `Bluetooth A2DP active returns non-zero latency`() {
        val btDevice = mock<android.media.AudioDeviceInfo>()
        whenever(btDevice.type).thenReturn(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        whenever(audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))
            .thenReturn(arrayOf(btDevice))

        val compensator = createCompensator()
        val latency = compensator.getEstimatedLatencyMs()
        assertTrue("Latency should be > 0 for BT", latency > 0L)
    }

    // --- compensatePosition adds latency to raw position ---

    @Test
    fun `compensatePosition adds latency to raw position`() {
        val btDevice = mock<android.media.AudioDeviceInfo>()
        whenever(btDevice.type).thenReturn(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        whenever(audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))
            .thenReturn(arrayOf(btDevice))

        val compensator = createCompensator()
        val raw = 30_000L
        val compensated = compensator.compensatePosition(raw)
        assertTrue("Compensated should be > raw", compensated > raw)
    }

    private fun assertTrue(
        message: String,
        condition: Boolean,
    ) {
        if (!condition) throw AssertionError(message)
    }
}
