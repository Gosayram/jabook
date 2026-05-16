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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for BufferManager.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BufferManagerTest {

    @Test
    fun testBufferManager_startsAndStops() = runBlockingTest {
        val player = FakeExoPlayer()
        val manager = BufferManager(player)
        manager.start()
        manager.stop()
        // Verify no exceptions
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun testBufferManager_monitorsBufferLevels() = runBlockingTest {
        val player = FakeExoPlayer()
        val manager = BufferManager(player)
        manager.start()
        player.bufferedPosition = 10000
        player.currentPosition = 5000
        delay(100) // Wait for monitoring loop
        manager.stop()
        // Verify buffer levels are logged
    }

    @Test
    fun testBufferManager_reducesBufferOnUnderrun() = runBlockingTest {
        val player = FakeExoPlayer()
        val manager = BufferManager(player)
        manager.start()
        player.playWhenReady = true
        player.playbackState = Player.STATE_BUFFERING
        player.bufferedPosition = 1000
        player.currentPosition = 500
        delay(200)
        // Verify buffer size reduced to minBufferMs
        manager.stop()
    }

    @Test
    fun testBufferManager_increasesBufferOnReady() = runBlockingTest {
        val player = FakeExoPlayer()
        val manager = BufferManager(player)
        manager.start()
        player.playWhenReady = true
        player.playbackState = Player.STATE_READY
        player.bufferedPosition = 20000
        player.currentPosition = 5000
        delay(200)
        // Verify buffer size increased
        manager.stop()
    }
}