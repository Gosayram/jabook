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

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PlaylistLoadProgress].
 */
class PlaylistLoadProgressTest {

    @Test
    fun `fraction is calculated correctly`() {
        val progress = PlaylistLoadProgress(loaded = 3, total = 10, phase = Phase.LOADING_BACKGROUND)
        assertEquals(0.3f, progress.fraction, 0.001f)
    }

    @Test
    fun `fraction is 0 when total is 0`() {
        val progress = PlaylistLoadProgress(loaded = 0, total = 0, phase = Phase.IDLE)
        assertEquals(0f, progress.fraction, 0.001f)
    }

    @Test
    fun `fraction is 1 when loaded equals total`() {
        val progress = PlaylistLoadProgress(loaded = 10, total = 10, phase = Phase.DONE)
        assertEquals(1f, progress.fraction, 0.001f)
    }
}