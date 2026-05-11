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

class InactivityResetPolicyTest {
    @Test
    fun `shouldReset is true for interactive local sources`() {
        assertTrue(InactivityResetPolicy.shouldReset(InactivityCommandSource.USER_UI))
        assertTrue(InactivityResetPolicy.shouldReset(InactivityCommandSource.NOTIFICATION))
        assertTrue(InactivityResetPolicy.shouldReset(InactivityCommandSource.PLAYBACK_INTERNAL))
    }

    @Test
    fun `shouldReset is false for remote and automation sources`() {
        assertFalse(InactivityResetPolicy.shouldReset(InactivityCommandSource.HEADSET_BUTTON))
        assertFalse(InactivityResetPolicy.shouldReset(InactivityCommandSource.ANDROID_AUTO))
        assertFalse(InactivityResetPolicy.shouldReset(InactivityCommandSource.WEAR_OS))
        assertFalse(InactivityResetPolicy.shouldReset(InactivityCommandSource.SLEEP_TIMER))
    }
}
