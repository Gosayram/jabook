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

public class AdaptiveDrcThresholdPolicyTest {
    @Test
    public fun `resolveThresholdDb returns default when measuredLufs is null`() {
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Gentle, null)
        assertEquals(
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Gentle),
            result,
            0.001f,
        )
    }

    @Test
    public fun `resolveThresholdDb returns default when DRCLevel is Off`() {
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Off, -16.0f)
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    public fun `resolveThresholdDb returns measuredLufs plus offset for quiet recordings`() {
        val measuredLufs = -25.0f
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, measuredLufs)
        assertEquals(-19.0f, result, 0.001f)
    }

    @Test
    public fun `resolveThresholdDb returns measuredLufs minus offset for loud recordings`() {
        val measuredLufs = -12.0f
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Strong, measuredLufs)
        assertEquals(-15.0f, result, 0.001f)
    }

    @Test
    public fun `resolveThresholdDb returns default for normal range recordings`() {
        val measuredLufs = -19.0f
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Medium, measuredLufs)
        assertEquals(
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Medium),
            result,
            0.001f,
        )
    }

    @Test
    public fun `resolveThresholdDb returns correct quiet boundary exactly at threshold`() {
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Gentle, -23.0f)
        assertEquals(
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Gentle),
            result,
            0.001f,
        )
    }

    @Test
    public fun `resolveThresholdDb returns correct loud boundary exactly at threshold`() {
        val result =
            AdaptiveDrcThresholdPolicy.resolveThresholdDb(DRCLevel.Strong, -16.0f)
        assertEquals(
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Strong),
            result,
            0.001f,
        )
    }

    @Test
    public fun `defaultThresholdDb returns correct values for all levels`() {
        assertEquals(0.0f, AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Off), 0.001f)
        assertEquals(
            -32.0f,
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Gentle),
            0.001f,
        )
        assertEquals(
            -24.0f,
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Medium),
            0.001f,
        )
        assertEquals(
            -18.0f,
            AdaptiveDrcThresholdPolicy.defaultThresholdDb(DRCLevel.Strong),
            0.001f,
        )
    }
}
