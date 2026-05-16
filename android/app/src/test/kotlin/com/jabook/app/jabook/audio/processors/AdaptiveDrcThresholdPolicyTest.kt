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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AdaptiveDrcThresholdPolicy].
 *
 * P-04: Verifies adaptive DRC threshold calibration based on LUFS.
 */
class AdaptiveDrcThresholdPolicyTest {

    // --- resolveThresholdDb: null LUFS → fallback to defaults ---

    @Test
    fun `resolveThresholdDb returns default when lufs is null`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, null)
        assertEquals(-24.0f, result, 0.01f)
    }

    @Test
    fun `resolveThresholdDb returns 0 when DRC is Off`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Off, -20.0f)
        assertEquals(0.0f, result, 0.01f)
    }

    // --- resolveThresholdDb: quiet recording (LUFS < -23) ---

    @Test
    fun `quiet recording raises threshold above measured LUFS`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Gentle, -26.0f)
        assertEquals(-20.0f, result, 0.01f)
    }

    @Test
    fun `very quiet recording gets significantly raised threshold`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, -30.0f)
        assertEquals(-24.0f, result, 0.01f)
    }

    // --- resolveThresholdDb: loud recording (LUFS > -16) ---

    @Test
    fun `loud recording lowers threshold below measured LUFS`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, -14.0f)
        assertEquals(-17.0f, result, 0.01f)
    }

    @Test
    fun `very loud recording gets significantly lowered threshold`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Strong, -10.0f)
        assertEquals(-13.0f, result, 0.01f)
    }

    // --- resolveThresholdDb: normal recording (-23..-16 LUFS) ---

    @Test
    fun `normal recording uses default threshold for Gentle`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Gentle, -20.0f)
        assertEquals(-32.0f, result, 0.01f)
    }

    @Test
    fun `normal recording uses default threshold for Medium`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, -20.0f)
        assertEquals(-24.0f, result, 0.01f)
    }

    @Test
    fun `normal recording uses default threshold for Strong`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Strong, -20.0f)
        assertEquals(-18.0f, result, 0.01f)
    }

    // --- defaultThresholdDb ---

    @Test
    fun `defaultThresholdDb Off returns 0`() {
        assertEquals(0.0f, AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Off), 0.01f)
    }

    @Test
    fun `defaultThresholdDb Gentle returns -32`() {
        assertEquals(-32.0f, AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Gentle), 0.01f)
    }

    @Test
    fun `defaultThresholdDb Medium returns -24`() {
        assertEquals(-24.0f, AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Medium), 0.01f)
    }

    @Test
    fun `defaultThresholdDb Strong returns -18`() {
        assertEquals(-18.0f, AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Strong), 0.01f)
    }

    // --- targetLufs ---

    @Test
    fun `targetLufs returns -16`() {
        assertEquals(-16.0f, AdaptiveDrcThresholdPolicy.targetLufs(), 0.01f)
    }

    // --- Edge cases ---

    @Test
    fun `boundary quiet -23 LUFS uses default`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Gentle, -23.0f)
        assertEquals(-32.0f, result, 0.01f)
    }

    @Test
    fun `boundary loud -16 LUFS uses default`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, -16.0f)
        assertEquals(-24.0f, result, 0.01f)
    }

    @Test
    fun `just below quiet boundary gets adaptive threshold`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, -23.1f)
        assertEquals(-17.1f, result, 0.01f)
    }

    @Test
    fun `just above loud boundary gets adaptive threshold`() {
        val result = AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, -15.9f)
        assertEquals(-18.9f, result, 0.01f)
    }
}
