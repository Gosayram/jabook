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

import android.content.Context
import androidx.media3.common.MediaItem
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
import coil.ImageLoader
import coil.request.ImageRequest
import coil.util.TestImageLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [TrackTransitionCoordinator].
 *
 * P-08: Tests cover art preloading to prevent UI flickering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackTransitionCoordinatorTest {

    private lateinit var context: Context
    private lateinit var trackTransitionCoordinator: TrackTransitionCoordinator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `preloadNextCover enqueues request with correct parameters`() = runTest {
        val testLoader = TestImageLoader(context)
        val mediaItem = MediaItem.Builder()
            .setMediaId("test-track")
            .setUri("file:///test/track1.mp3")
            .setArtworkUri("https://example.com/cover.jpg")
            .build()

        trackTransitionCoordinator = TrackTransitionCoordinator(
            coilImageLoader = testLoader,
            context = context,
        )

        trackTransitionCoordinator.preloadNextCover(mediaItem)

        // Verify that a request was enqueued
        val request = testLoader.lastEnqueuedRequest
        assertNotNull("Request should be enqueued", request)

        // Verify parameters
        assertEquals("https://example.com/cover.jpg", request.data.toString())
        assertEquals(CachePolicy.ENABLED, request.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, request.diskCachePolicy)
        assertEquals(512, request.size?.first)
        assertEquals(512, request.size?.second)

        trackTransitionCoordinator.release()
    }

    @Test
    fun `preloadNextCover does nothing when artworkUri is null`() = runTest {
        val testLoader = TestImageLoader(context)
        val mediaItem = MediaItem.Builder()
            .setMediaId("test-track")
            .setUri("file:///test/track1.mp3")
            // No artworkUri
            .build()

        trackTransitionCoordinator = TrackTransitionCoordinator(
            coilImageLoader = testLoader,
            context = context,
        )

        trackTransitionCoordinator.preloadNextCover(mediaItem)

        // Verify that no request was enqueued
        assertNull("Request should not be enqueued", testLoader.lastEnqueuedRequest)

        trackTransitionCoordinator.release()
    }

    @Test
    fun `preloadNextCover does nothing when dependencies are null`() = runTest {
        val testLoader = TestImageLoader(context)
        val mediaItem = MediaItem.Builder()
            .setMediaId("test-track")
            .setUri("file:///test/track1.mp3")
            .setArtworkUri("https://example.com/cover.jpg")
            .build()

        // Test with null coilImageLoader
        trackTransitionCoordinator = TrackTransitionCoordinator(
            coilImageLoader = null,
            context = context,
        )
        trackTransitionCoordinator.preloadNextCover(mediaItem)

        // Test with null context
        trackTransitionCoordinator = TrackTransitionCoordinator(
            coilImageLoader = testLoader,
            context = null,
        )
        trackTransitionCoordinator.preloadNextCover(mediaItem)

        trackTransitionCoordinator.release()
    }
}