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

package com.jabook.app.jabook.audio.processors

import com.jabook.app.jabook.compose.feature.player.ChapterRepeatMode
import com.jabook.app.jabook.compose.feature.player.PlayerReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GaplessTransitionPolicyTest {
    @Test
    fun `gapless is impossible when crossfade is enabled`() {
        val settings = AudioProcessingSettings(isCrossfadeEnabled = true, crossfadeDurationMs = 3000L)
        assertFalse(GaplessTransitionPolicy.isGaplessPossible(settings))
    }

    @Test
    fun `gapless is possible with default settings`() {
        val settings = AudioProcessingSettings.defaults()
        assertTrue(GaplessTransitionPolicy.isGaplessPossible(settings))
    }

    @Test
    fun `chapter repeat mode cycles across all states`() {
        val once = PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.OFF)
        val infinite = PlayerReducer.nextChapterRepeatMode(once)
        val off = PlayerReducer.nextChapterRepeatMode(infinite)

        assertEquals(ChapterRepeatMode.ONCE, once)
        assertEquals(ChapterRepeatMode.INFINITE, infinite)
        assertEquals(ChapterRepeatMode.OFF, off)
    }
}
