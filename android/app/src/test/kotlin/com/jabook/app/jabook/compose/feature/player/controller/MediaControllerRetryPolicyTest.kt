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

package com.jabook.app.jabook.compose.feature.player.controller

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaControllerRetryPolicyTest {
    @Test
    fun `uses a short bounded exponential backoff`() {
        assertEquals(3, MediaControllerRetryPolicy.MAX_RETRIES)
        assertEquals(250L, MediaControllerRetryPolicy.delayMs(1))
        assertEquals(500L, MediaControllerRetryPolicy.delayMs(2))
        assertEquals(1_000L, MediaControllerRetryPolicy.delayMs(3))
        assertEquals(1_000L, MediaControllerRetryPolicy.delayMs(10))
    }
}
