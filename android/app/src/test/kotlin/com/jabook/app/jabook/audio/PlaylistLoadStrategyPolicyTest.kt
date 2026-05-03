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

class PlaylistLoadStrategyPolicyTest {
    @Test
    fun `select returns SYNC below threshold`() {
        assertEquals(PlaylistLoadStrategy.SYNC, PlaylistLoadStrategyPolicy.select(1))
        assertEquals(PlaylistLoadStrategy.SYNC, PlaylistLoadStrategyPolicy.select(49))
    }

    @Test
    fun `select returns ASYNC at and above threshold`() {
        assertEquals(PlaylistLoadStrategy.ASYNC, PlaylistLoadStrategyPolicy.select(50))
        assertEquals(PlaylistLoadStrategy.ASYNC, PlaylistLoadStrategyPolicy.select(200))
    }
}
