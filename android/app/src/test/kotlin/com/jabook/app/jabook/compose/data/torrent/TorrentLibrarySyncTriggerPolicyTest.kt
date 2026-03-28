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

package com.jabook.app.jabook.compose.data.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentLibrarySyncTriggerPolicyTest {
    @Test
    fun `shouldTrigger returns true when cooldown elapsed`() {
        assertTrue(
            TorrentLibrarySyncTriggerPolicy.shouldTrigger(
                lastTriggeredAtMs = 1_000L,
                nowMs = 5_000L,
                cooldownMs = 3_000L,
            ),
        )
    }

    @Test
    fun `shouldTrigger returns false when cooldown not elapsed`() {
        assertFalse(
            TorrentLibrarySyncTriggerPolicy.shouldTrigger(
                lastTriggeredAtMs = 1_000L,
                nowMs = 3_500L,
                cooldownMs = 3_000L,
            ),
        )
    }
}
