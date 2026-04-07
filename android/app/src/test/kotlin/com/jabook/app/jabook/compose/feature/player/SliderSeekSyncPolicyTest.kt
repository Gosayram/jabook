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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderSeekSyncPolicyTest {
    @Test
    fun `keeps slider unchanged while dragging`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 0.9f,
                currentSliderPosition = 0.25f,
                isDragging = true,
                awaitingSeekSync = false,
            )

        assertEquals(0.25f, result.sliderPosition, 0.0001f)
        assertFalse(result.awaitingSeekSync)
    }

    @Test
    fun `updates slider immediately when not awaiting seek sync`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 0.8f,
                currentSliderPosition = 0.3f,
                isDragging = false,
                awaitingSeekSync = false,
            )

        assertEquals(0.8f, result.sliderPosition, 0.0001f)
        assertFalse(result.awaitingSeekSync)
    }

    @Test
    fun `keeps waiting when player progress not converged`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 0.4f,
                currentSliderPosition = 0.8f,
                isDragging = false,
                awaitingSeekSync = true,
            )

        assertEquals(0.8f, result.sliderPosition, 0.0001f)
        assertTrue(result.awaitingSeekSync)
    }

    @Test
    fun `stops waiting when player progress converges`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 0.79f,
                currentSliderPosition = 0.8f,
                isDragging = false,
                awaitingSeekSync = true,
            )

        assertEquals(0.79f, result.sliderPosition, 0.0001f)
        assertFalse(result.awaitingSeekSync)
    }

    @Test
    fun `sanitizes non finite current slider position`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = Float.NaN,
                currentSliderPosition = Float.POSITIVE_INFINITY,
                isDragging = false,
                awaitingSeekSync = true,
            )

        assertEquals(0f, result.sliderPosition, 0.0001f)
        assertTrue(result.awaitingSeekSync)
    }

    @Test
    fun `clamps player progress to valid range`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 1.7f,
                currentSliderPosition = 0.3f,
                isDragging = false,
                awaitingSeekSync = false,
            )

        assertEquals(1f, result.sliderPosition, 0.0001f)
        assertFalse(result.awaitingSeekSync)
    }

    @Test
    fun `keeps awaiting seek sync during drag cancel window`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 0.5f,
                currentSliderPosition = 0.75f,
                isDragging = true,
                awaitingSeekSync = true,
            )

        assertEquals(0.75f, result.sliderPosition, 0.0001f)
        assertTrue(result.awaitingSeekSync)
    }

    @Test
    fun `finishes seek sync after drag when converged on next tick`() {
        val result =
            SliderSeekSyncPolicy.resolveFromPlayerProgress(
                playerProgress = 0.601f,
                currentSliderPosition = 0.6f,
                isDragging = false,
                awaitingSeekSync = true,
            )

        assertEquals(0.601f, result.sliderPosition, 0.0001f)
        assertFalse(result.awaitingSeekSync)
    }
}
