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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMemoryHierarchyTest {
    @Test
    fun `resolveSpeed prefers per-book first`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = 1.6f,
                perNarratorSpeed = 1.5f,
                perAuthorSpeed = 1.4f,
                globalSpeed = 1.0f,
            )

        assertEquals(1.6f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed falls back to narrator then author then global`() {
        val narratorResolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = 1.45f,
                perAuthorSpeed = 1.35f,
                globalSpeed = 1.0f,
            )
        val authorResolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = 1.35f,
                globalSpeed = 1.0f,
            )
        val globalResolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = null,
                globalSpeed = 1.0f,
            )

        assertEquals(1.45f, narratorResolved, 0.0001f)
        assertEquals(1.35f, authorResolved, 0.0001f)
        assertEquals(1.0f, globalResolved, 0.0001f)
    }

    @Test
    fun `hasMeaningfulSpeedDelta ignores tiny changes`() {
        assertFalse(
            SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                previousSpeed = 1.50f,
                newSpeed = 1.505f,
            ),
        )
        assertTrue(
            SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                previousSpeed = 1.50f,
                newSpeed = 1.53f,
            ),
        )
        assertTrue(
            SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                previousSpeed = null,
                newSpeed = 1.25f,
            ),
        )
    }
}
