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

class CrossfadeRequestStalenessPolicyTest {
    @Test
    fun `accepts result for the same monitoring generation player and chapter`() {
        val player = Any()

        assertTrue(
            CrossfadeRequestStalenessPolicy.isCurrent(
                activeGeneration = 7L,
                requestGeneration = 7L,
                activePlayer = player,
                requestPlayer = player,
                activeChapterIndex = 4,
                requestChapterIndex = 4,
            ),
        )
    }

    @Test
    fun `rejects result after monitoring has been stopped or restarted`() {
        val player = Any()

        assertFalse(
            CrossfadeRequestStalenessPolicy.isCurrent(
                activeGeneration = 8L,
                requestGeneration = 7L,
                activePlayer = player,
                requestPlayer = player,
                activeChapterIndex = 4,
                requestChapterIndex = 4,
            ),
        )
    }

    @Test
    fun `rejects result after active player switches`() {
        assertFalse(
            CrossfadeRequestStalenessPolicy.isCurrent(
                activeGeneration = 7L,
                requestGeneration = 7L,
                activePlayer = Any(),
                requestPlayer = Any(),
                activeChapterIndex = 4,
                requestChapterIndex = 4,
            ),
        )
    }

    @Test
    fun `rejects result after user seeks to another chapter`() {
        val player = Any()

        assertFalse(
            CrossfadeRequestStalenessPolicy.isCurrent(
                activeGeneration = 7L,
                requestGeneration = 7L,
                activePlayer = player,
                requestPlayer = player,
                activeChapterIndex = 5,
                requestChapterIndex = 4,
            ),
        )
    }
}
