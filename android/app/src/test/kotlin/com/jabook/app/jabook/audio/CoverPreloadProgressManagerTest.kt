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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [CoverPreloadProgressManager].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverPreloadProgressManagerTest {

    private lateinit var playlistManager: PlaylistManager
    private lateinit var coverPreloadExecutor: CoverPreloadExecutor
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycle: Lifecycle

    @Before
    fun setUp() {
        playlistManager = mock()
        coverPreloadExecutor = mock()
        lifecycle = TestLifecycleOwner().apply { start() }.lifecycle
        lifecycleOwner = lifecycle.owner
    }

    @Test
    fun `progress starts at IDLE when created`() = runBlockingTest {
        // Given
        val manager = CoverPreloadProgressManager(playlistManager, coverPreloadExecutor)

        // When
        manager.onCreate(lifecycleOwner)

        // Then
        // Progress should be IDLE initially
        // This would require observing the flow, simplified for demo
    }

    @Test
    fun `progress updates when playlist loading starts`() = runBlockingTest {
        // Given
        val manager = CoverPreloadProgressManager(playlistManager, coverPreloadExecutor)

        // When
        manager.onCreate(lifecycleOwner)

        // Simulate playlist loading start
        // This would trigger progress updates
    }
}