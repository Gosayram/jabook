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
import org.junit.Test

class PlaybackSpeedSheetPolicyTest {
    @Test
    fun addRecentSpeed_deduplicatesWithinTolerance() {
        val recent = mutableListOf(1.0f, 1.25f, 1.5f)

        addRecentSpeed(recent, 1.249f)

        assertEquals(listOf(1.249f, 1.0f, 1.5f), recent)
    }

    @Test
    fun addRecentSpeed_insertsAtFrontAndCapsAtThree() {
        val recent = mutableListOf(1.0f, 1.25f, 1.5f)

        addRecentSpeed(recent, 2.0f)

        assertEquals(listOf(2.0f, 1.0f, 1.25f), recent)
    }

    @Test
    fun addRecentSpeed_existingValueMovesToFront() {
        val recent = mutableListOf(0.75f, 1.0f, 1.25f)

        addRecentSpeed(recent, 1.0f)

        assertEquals(listOf(1.0f, 0.75f, 1.25f), recent)
    }

    @Test
    fun addRecentSpeed_keepsDistinctValuesOutsideTolerance() {
        val recent = mutableListOf(1.0f, 1.25f, 1.5f)

        addRecentSpeed(recent, 1.261f)

        assertEquals(listOf(1.261f, 1.0f, 1.25f), recent)
    }

    @Test
    fun addRecentSpeed_whenListFullDropsOldestAfterInsert() {
        val recent = mutableListOf(0.8f, 1.0f, 1.2f)

        addRecentSpeed(recent, 1.4f)

        assertEquals(listOf(1.4f, 0.8f, 1.0f), recent)
    }
}
