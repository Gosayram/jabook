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

import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.VolumeWriteCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.math.pow

/**
 * Unit tests for [ChapterLoudnessTransitionPolicy].
 *
 * Verifies gain computation across chapter transitions (EBU R128 target of -23 LUFS),
 * no-op behavior for missing/equal LUFS values, and baseline tracking across nulls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChapterLoudnessTransitionPolicyTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: ExoPlayer
    private val volumes = mutableListOf<Float>()
    private var lufsCalls: Int = 0

    @Before
    fun setup() {
        player = mock()
        whenever(player.volume).thenReturn(START_VOLUME)
        doAnswer {
            volumes.add(it.getArgument(0))
            Unit
        }.whenever(player).volume = any()
    }

    private fun policyWith(vararg lufs: Double?): ChapterLoudnessTransitionPolicy {
        val byChapter = lufs.mapIndexed { index, value -> index to value }.toMap()
        return ChapterLoudnessTransitionPolicy(
            getActivePlayer = { player },
            getChapterLufs = { _, chapterIndex ->
                lufsCalls++
                byChapter[chapterIndex]
            },
            scope = testScope,
            volumeWriteCoordinator = VolumeWriteCoordinator(),
        )
    }

    @Test
    fun `first chapter records gain without animation`() {
        val policy = policyWith(-20.0)
        policy.onBookChanged(BOOK_ID)
        policy.onChapterTransition(0)
        testScope.advanceUntilIdle()

        assertTrue(volumes.isEmpty())
    }

    @Test
    fun `transition applies ratio of chapter gains`() {
        // gain(-20) = 10^(-3/20), gain(-14) = 10^(-9/20); ratio = 10^(-0.3) ≈ 0.5012
        val policy = policyWith(-20.0, -14.0)
        policy.onBookChanged(BOOK_ID)
        policy.onChapterTransition(0)
        testScope.advanceUntilIdle()
        policy.onChapterTransition(1)
        testScope.advanceUntilIdle()

        assertTrue(volumes.isNotEmpty())
        val expected = START_VOLUME * 10.0.pow(-0.3)
        assertEquals(expected.toFloat(), volumes.last(), 0.005f)
    }

    @Test
    fun `null LUFS keeps previous chapter baseline`() {
        // Chapter 1 has no LUFS; chapter 2 must still transition relative to chapter 0's gain.
        val policy = policyWith(-20.0, null, -14.0)
        policy.onBookChanged(BOOK_ID)
        policy.onChapterTransition(0)
        testScope.advanceUntilIdle()
        policy.onChapterTransition(1)
        testScope.advanceUntilIdle()
        policy.onChapterTransition(2)
        testScope.advanceUntilIdle()

        assertTrue(volumes.isNotEmpty())
        val expected = START_VOLUME * 10.0.pow(-0.3)
        assertEquals(expected.toFloat(), volumes.last(), 0.005f)
    }

    @Test
    fun `all-null LUFS applies no transition`() {
        val policy = policyWith(null, null)
        policy.onBookChanged(BOOK_ID)
        policy.onChapterTransition(0)
        testScope.advanceUntilIdle()
        policy.onChapterTransition(1)
        testScope.advanceUntilIdle()

        assertTrue(volumes.isEmpty())
        assertEquals(2, lufsCalls)
    }

    @Test
    fun `equal LUFS applies no transition`() {
        val policy = policyWith(-18.0, -18.0)
        policy.onBookChanged(BOOK_ID)
        policy.onChapterTransition(0)
        testScope.advanceUntilIdle()
        policy.onChapterTransition(1)
        testScope.advanceUntilIdle()

        assertTrue(volumes.isEmpty())
    }

    @Test
    fun `no book set means no LUFS lookup`() {
        val policy = policyWith(-20.0)
        policy.onChapterTransition(0)
        testScope.advanceUntilIdle()

        assertEquals(0, lufsCalls)
        assertTrue(volumes.isEmpty())
    }

    private companion object {
        private const val BOOK_ID = "book-1"
        private const val START_VOLUME = 0.3f
    }
}
