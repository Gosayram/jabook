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

    @Test
    fun `shouldRecordBookSpeed waits for trusted listening window`() {
        assertFalse(
            SpeedMemoryHierarchy.shouldRecordBookSpeed(
                listenedMs = SpeedMemoryHierarchy.MIN_TRUSTED_LISTENING_MS - 1L,
                previousSpeed = null,
                newSpeed = 1.25f,
            ),
        )
        assertTrue(
            SpeedMemoryHierarchy.shouldRecordBookSpeed(
                listenedMs = SpeedMemoryHierarchy.MIN_TRUSTED_LISTENING_MS,
                previousSpeed = null,
                newSpeed = 1.25f,
            ),
        )
    }

    @Test
    fun `shouldRecordBookSpeed ignores repeated speed inside tolerance`() {
        assertFalse(
            SpeedMemoryHierarchy.shouldRecordBookSpeed(
                listenedMs = SpeedMemoryHierarchy.MIN_TRUSTED_LISTENING_MS,
                previousSpeed = 1.25f,
                newSpeed = 1.255f,
            ),
        )
    }

    @Test
    fun `resolveSpeed skips NaN perBookSpeed and falls back to narrator`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = Float.NaN,
                perNarratorSpeed = 1.5f,
                perAuthorSpeed = 1.4f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.5f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips positive infinity perBookSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = Float.POSITIVE_INFINITY,
                perNarratorSpeed = 1.5f,
                perAuthorSpeed = 1.4f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.5f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips negative infinity perBookSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = Float.NEGATIVE_INFINITY,
                perNarratorSpeed = 1.5f,
                perAuthorSpeed = 1.4f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.5f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips zero perBookSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = 0f,
                perNarratorSpeed = 1.5f,
                perAuthorSpeed = 1.4f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.5f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips negative perBookSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = -1.5f,
                perNarratorSpeed = 1.5f,
                perAuthorSpeed = 1.4f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.5f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips NaN perNarratorSpeed and falls back to author`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = Float.NaN,
                perAuthorSpeed = 1.35f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.35f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips positive infinity perNarratorSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = Float.POSITIVE_INFINITY,
                perAuthorSpeed = 1.35f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.35f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips negative perNarratorSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = -2.0f,
                perAuthorSpeed = 1.35f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.35f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips invalid perAuthorSpeed and falls back to global`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = -2.0f,
                globalSpeed = 1.0f,
            )
        assertEquals(1.0f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips NaN perAuthorSpeed`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = Float.NaN,
                globalSpeed = 1.0f,
            )
        assertEquals(1.0f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed defaults to 1f when globalSpeed is NaN`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = null,
                globalSpeed = Float.NaN,
            )
        assertEquals(1f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed defaults to 1f when globalSpeed is positive infinity`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = null,
                globalSpeed = Float.POSITIVE_INFINITY,
            )
        assertEquals(1f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed defaults to 1f when globalSpeed is zero`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = null,
                globalSpeed = 0f,
            )
        assertEquals(1f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed defaults to 1f when globalSpeed is negative`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = null,
                perNarratorSpeed = null,
                perAuthorSpeed = null,
                globalSpeed = -1.0f,
            )
        assertEquals(1f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed skips all invalid candidates and defaults to 1f`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = Float.NaN,
                perNarratorSpeed = Float.POSITIVE_INFINITY,
                perAuthorSpeed = -0.5f,
                globalSpeed = 0f,
            )
        assertEquals(1f, resolved, 0.0001f)
    }

    @Test
    fun `resolveSpeed picks valid narrator when perBook is invalid and others are null`() {
        val resolved =
            SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = 0f,
                perNarratorSpeed = 1.8f,
                perAuthorSpeed = null,
                globalSpeed = 1.0f,
            )
        assertEquals(1.8f, resolved, 0.0001f)
    }

    @Test
    fun `hasMeaningfulSpeedDelta returns false when difference equals epsilon exactly`() {
        assertFalse(
            SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                previousSpeed = 1.0f,
                newSpeed = 1.01f,
                epsilon = 0.01f,
            ),
        )
    }

    @Test
    fun `hasMeaningfulSpeedDelta returns true when difference just exceeds epsilon`() {
        assertTrue(
            SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                previousSpeed = 1.0f,
                newSpeed = 1.010001f,
                epsilon = 0.01f,
            ),
        )
    }

    @Test
    fun `hasMeaningfulSpeedDelta returns false when speeds are identical`() {
        assertFalse(
            SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                previousSpeed = 1.5f,
                newSpeed = 1.5f,
            ),
        )
    }
}
