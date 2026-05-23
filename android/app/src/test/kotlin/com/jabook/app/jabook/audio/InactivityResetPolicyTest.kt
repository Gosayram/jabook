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

import org.junit.Test
import kotlin.test.assertEquals

class InactivityResetPolicyTest {
    @Test
    fun `USER_UI resets timer`() {
        assertEquals(true, InactivityResetPolicy.shouldReset(InactivityCommandSource.USER_UI))
    }

    @Test
    fun `HEADSET_BUTTON does not reset timer`() {
        assertEquals(false, InactivityResetPolicy.shouldReset(InactivityCommandSource.HEADSET_BUTTON))
    }

    @Test
    fun `ANDROID_AUTO does not reset timer`() {
        assertEquals(false, InactivityResetPolicy.shouldReset(InactivityCommandSource.ANDROID_AUTO))
    }

    @Test
    fun `WEAR_OS does not reset timer`() {
        assertEquals(false, InactivityResetPolicy.shouldReset(InactivityCommandSource.WEAR_OS))
    }

    @Test
    fun `SLEEP_TIMER does not reset timer`() {
        assertEquals(false, InactivityResetPolicy.shouldReset(InactivityCommandSource.SLEEP_TIMER))
    }

    @Test
    fun `NOTIFICATION resets timer`() {
        assertEquals(true, InactivityResetPolicy.shouldReset(InactivityCommandSource.NOTIFICATION))
    }

    @Test
    fun `PLAYBACK_INTERNAL resets timer`() {
        assertEquals(true, InactivityResetPolicy.shouldReset(InactivityCommandSource.PLAYBACK_INTERNAL))
    }
}
