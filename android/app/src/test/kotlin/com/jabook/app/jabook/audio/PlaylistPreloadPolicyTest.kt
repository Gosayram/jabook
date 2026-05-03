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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPreloadPolicyTest {
    @Test
    fun `decide returns skip no paths when playlist is absent`() {
        val decision = PlaylistPreloadPolicy.decide(playlistSize = null, targetIndex = 0, alreadyLoaded = false)
        assertEquals(PreloadDecision.SKIP_NO_PATHS, decision)
    }

    @Test
    fun `decide returns skip out of bounds for invalid index`() {
        val negative = PlaylistPreloadPolicy.decide(playlistSize = 5, targetIndex = -1, alreadyLoaded = false)
        val tooLarge = PlaylistPreloadPolicy.decide(playlistSize = 5, targetIndex = 5, alreadyLoaded = false)
        assertEquals(PreloadDecision.SKIP_OUT_OF_BOUNDS, negative)
        assertEquals(PreloadDecision.SKIP_OUT_OF_BOUNDS, tooLarge)
    }

    @Test
    fun `decide returns already loaded when target is present`() {
        val decision = PlaylistPreloadPolicy.decide(playlistSize = 5, targetIndex = 3, alreadyLoaded = true)
        assertEquals(PreloadDecision.SKIP_ALREADY_LOADED, decision)
    }

    @Test
    fun `decide returns preload for valid and unloaded track`() {
        val decision = PlaylistPreloadPolicy.decide(playlistSize = 5, targetIndex = 3, alreadyLoaded = false)
        assertEquals(PreloadDecision.PRELOAD, decision)
    }

    @Test
    fun `shouldAttachAfterBuild mirrors still needed flag`() {
        assertTrue(PlaylistPreloadPolicy.shouldAttachAfterBuild(true))
        assertFalse(PlaylistPreloadPolicy.shouldAttachAfterBuild(false))
    }
}
