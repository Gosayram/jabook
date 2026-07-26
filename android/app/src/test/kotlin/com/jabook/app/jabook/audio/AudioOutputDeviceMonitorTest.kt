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
import org.junit.Test

class AudioOutputDeviceMonitorTest {
    @Test
    fun `OutputType enum has all expected values`() {
        val values = AudioOutputDeviceMonitor.OutputType.values()
        assertEquals(4, values.size)
        assertEquals(
            AudioOutputDeviceMonitor.OutputType.SPEAKER,
            AudioOutputDeviceMonitor.OutputType.valueOf("SPEAKER"),
        )
        assertEquals(
            AudioOutputDeviceMonitor.OutputType.WIRED_HEADPHONE,
            AudioOutputDeviceMonitor.OutputType.valueOf("WIRED_HEADPHONE"),
        )
        assertEquals(
            AudioOutputDeviceMonitor.OutputType.BLUETOOTH,
            AudioOutputDeviceMonitor.OutputType.valueOf("BLUETOOTH"),
        )
        assertEquals(
            AudioOutputDeviceMonitor.OutputType.USB,
            AudioOutputDeviceMonitor.OutputType.valueOf("USB"),
        )
    }

    @Test
    fun `OutputType values are distinct by name`() {
        val values = AudioOutputDeviceMonitor.OutputType.values().toList()
        val names = values.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `OutputType enum entries count matches expected count`() {
        // Verify the enum has exactly 4 entries as expected for audio output types
        assertEquals(4, AudioOutputDeviceMonitor.OutputType.values().size)
    }
}
