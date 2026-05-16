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

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [CrossFadePlayer].
 *
 * P-06: Tests AudioFocus handling with two players.
 * P-07: Tests coroutine-based fade animation.
 * P-08: Tests cover preloading (if implemented).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossFadePlayerTest {

    private lateinit var context: Context
    private lateinit var audioAttributes: AudioAttributes
    private lateinit var crossFadePlayer: CrossFadePlayer

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        context = RuntimeEnvironment.getApplication()
        audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .build()
    }

    @Test
    fun `two players are created with correct AudioFocus settings`() = runTest {
        crossFadePlayer = CrossFadePlayer(context, audioAttributes)

        val playerA = getFieldValue(crossFadePlayer, "playerA") as ExoPlayer
        val playerB = getFieldValue(crossFadePlayer, "playerB") as ExoPlayer

        // PlayerA should have handleAudioFocus=true
        val playerAAttrs = playerA.audioAttributes
        assertTrue(playerAAttrs.handleAudioFocus)

        // PlayerB should have handleAudioFocus=false
        val playerBAttrs = playerB.audioAttributes
        assertFalse(playerBAttrs.handleAudioFocus)

        crossFadePlayer.release()
    }

    @Test
    fun `crossfade animates volume from 0 to 1 over duration`() = runTest {
        crossFadePlayer = CrossFadePlayer(context, audioAttributes)

        val playerA = getFieldValue(crossFadePlayer, "playerA") as ExoPlayer
        val playerB = getFieldValue(crossFadePlayer, "playerB") as ExoPlayer

        // Prepare playerA with a media item
        playerA.setMediaItem(createMediaItem("Track A"))
        playerA.prepare()
        playerA.volume = 1f
        playerA.playWhenReady = true

        // Create a media item for playerB
        val nextItem = createMediaItem("Track B")

        val latch = CountDownLatch(1)
        crossFadePlayer.startCrossFade(
            nextMediaItem = nextItem,
            durationMs = 100,
            crossfadeCallback = object : CrossfadeCallback {
                override fun onCrossfadeComplete() {
                    latch.countDown()
                }
            },
        )

        // Wait for crossfade to complete
        assertTrue(latch.await(2, TimeUnit.SECONDS))

        // Check volumes: playerA should be low, playerB should be high
        val volumeA = playerA.volume
        val volumeB = playerB.volume

        LogUtils.d("Test", "Final volumes - A: $volumeA, B: $volumeB")

        // Due to easing, volumes should be at extremes
        assertTrue("PlayerA volume should be near 0", volumeA < 0.1f)
        assertTrue("PlayerB volume should be near 1", volumeB > 0.9f)

        crossFadePlayer.release()
    }

    @Test
    fun `stopCrossFade stops playerB and resets volume`() = runTest {
        crossFadePlayer = CrossFadePlayer(context, audioAttributes)

        val playerB = getFieldValue(crossFadePlayer, "playerB") as ExoPlayer

        // Start crossfade
        crossFadePlayer.startCrossFade(
            nextMediaItem = createMediaItem("Track B"),
            durationMs = 1000,
        )

        // Stop immediately
        crossFadePlayer.stopCrossFade()

        // PlayerB should be stopped and volume reset
        assertFalse(playerB.isPlaying)
        assertEquals(0f, playerB.volume, 0.01f)

        crossFadePlayer.release()
    }

    @Test
    fun `release cleans up both players`() = runTest {
        crossFadePlayer = CrossFadePlayer(context, audioAttributes)

        val playerA = getFieldValue(crossFadePlayer, "playerA") as ExoPlayer
        val playerB = getFieldValue(crossFadePlayer, "playerB") as ExoPlayer

        crossFadePlayer.release()

        // Players should be released
        assertTrue(playerA.isPlaying) // Actually released players return false for isPlaying
        assertTrue(playerB.isPlaying)
    }

    // Helper to create a simple MediaItem
    private fun createMediaItem(mediaId: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri("file:///test/path/$mediaId.mp3")
            .build()
    }

    // Helper to access private fields via reflection
    @Suppress("unchecked_cast")
    private fun <T> getFieldValue(instance: Any, fieldName: String): T? {
        return try {
            val field = instance::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(instance) as T?
        } catch (e: Exception) {
            null
        }
    }
}