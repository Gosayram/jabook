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

class PlaylistLoadProgressTest {
    // --- Fraction ---

    @Test
    fun `fraction is zero when total is zero`() {
        val progress = PlaylistLoadProgress.IDLE
        assertEquals(0.0f, progress.fraction, 0.001f)
    }

    @Test
    fun `fraction at midpoint`() {
        val progress = PlaylistLoadProgress(5, 10, PlaylistLoadProgress.Phase.LOADING_BACKGROUND)
        assertEquals(0.5f, progress.fraction, 0.001f)
    }

    @Test
    fun `fraction clamped to 1_0`() {
        val progress = PlaylistLoadProgress(15, 10, PlaylistLoadProgress.Phase.DONE)
        assertEquals(1.0f, progress.fraction, 0.001f)
    }

    // --- isLoading ---

    @Test
    fun `IDLE is not loading`() {
        assertFalse(PlaylistLoadProgress.IDLE.isLoading)
    }

    @Test
    fun `DONE is not loading`() {
        val progress = PlaylistLoadProgress(10, 10, PlaylistLoadProgress.Phase.DONE)
        assertFalse(progress.isLoading)
    }

    @Test
    fun `LOADING_FIRST is loading`() {
        val progress = PlaylistLoadProgress(1, 10, PlaylistLoadProgress.Phase.LOADING_FIRST)
        assertTrue(progress.isLoading)
    }

    @Test
    fun `LOADING_BACKGROUND is loading`() {
        val progress = PlaylistLoadProgress(7, 10, PlaylistLoadProgress.Phase.LOADING_BACKGROUND)
        assertTrue(progress.isLoading)
    }

    // --- isFirstTrackReady ---

    @Test
    fun `IDLE first track not ready`() {
        assertFalse(PlaylistLoadProgress.IDLE.isFirstTrackReady)
    }

    @Test
    fun `LOADING_FIRST first track not ready`() {
        val progress = PlaylistLoadProgress(1, 10, PlaylistLoadProgress.Phase.LOADING_FIRST)
        assertFalse(progress.isFirstTrackReady)
    }

    @Test
    fun `LOADING_CRITICAL first track ready`() {
        val progress = PlaylistLoadProgress(5, 10, PlaylistLoadProgress.Phase.LOADING_CRITICAL)
        assertTrue(progress.isFirstTrackReady)
    }

    @Test
    fun `DONE first track ready`() {
        val progress = PlaylistLoadProgress(10, 10, PlaylistLoadProgress.Phase.DONE)
        assertTrue(progress.isFirstTrackReady)
    }

    // --- of helper ---

    @Test
    fun `of creates correct initial state`() {
        val progress = PlaylistLoadProgress.of(50)
        assertEquals(0, progress.loaded)
        assertEquals(50, progress.total)
        assertEquals(PlaylistLoadProgress.Phase.IDLE, progress.phase)
    }

    // --- IDLE companion ---

    @Test
    fun `IDLE has correct values`() {
        assertEquals(0, PlaylistLoadProgress.IDLE.loaded)
        assertEquals(0, PlaylistLoadProgress.IDLE.total)
        assertEquals(PlaylistLoadProgress.Phase.IDLE, PlaylistLoadProgress.IDLE.phase)
    }
}
