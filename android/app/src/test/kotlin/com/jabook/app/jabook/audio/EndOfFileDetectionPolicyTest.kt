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

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class EndOfFileDetectionPolicyTest {
    @Test
    fun `returns minimum threshold for unset duration`() {
        val threshold = EndOfFileDetectionPolicy.calculateThresholdMs(C.TIME_UNSET)
        assertEquals(2000L, threshold)
    }

    @Test
    fun `returns minimum threshold for short files`() {
        val threshold = EndOfFileDetectionPolicy.calculateThresholdMs(60_000L)
        assertEquals(2000L, threshold)
    }

    @Test
    fun `returns proportional threshold for mid size files`() {
        val threshold = EndOfFileDetectionPolicy.calculateThresholdMs(300_000L)
        assertEquals(3000L, threshold)
    }

    @Test
    fun `caps threshold for very long files`() {
        val threshold = EndOfFileDetectionPolicy.calculateThresholdMs(900_000L)
        assertEquals(5000L, threshold)
    }
}

