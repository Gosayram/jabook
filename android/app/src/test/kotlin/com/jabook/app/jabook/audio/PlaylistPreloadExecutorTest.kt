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

import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPreloadExecutorTest {
    @Test
    fun `execute returns Attached and invokes attach when track still needed`() =
        runTest {
            val executor = PlaylistPreloadExecutor(mainDispatcher = StandardTestDispatcher(testScheduler))
            val mediaSource = mock<MediaSource>()
            var attached = false

            val result =
                executor.execute(
                    buildMediaSource = { mediaSource },
                    shouldAttachOnMain = { true },
                    attachOnMain = {
                        attached = true
                    },
                )

            assertEquals(PlaylistPreloadExecutionResult.Attached, result)
            assertTrue(attached)
        }

    @Test
    fun `execute returns SkippedAlreadyAvailable when track no longer needed`() =
        runTest {
            val executor = PlaylistPreloadExecutor(mainDispatcher = StandardTestDispatcher(testScheduler))
            val mediaSource = mock<MediaSource>()
            var attached = false

            val result =
                executor.execute(
                    buildMediaSource = { mediaSource },
                    shouldAttachOnMain = { false },
                    attachOnMain = { attached = true },
                )

            assertEquals(PlaylistPreloadExecutionResult.SkippedAlreadyAvailable, result)
            assertTrue(!attached)
        }

    @Test
    fun `execute returns Failed when source build throws`() =
        runTest {
            val executor = PlaylistPreloadExecutor(mainDispatcher = StandardTestDispatcher(testScheduler))

            val result =
                executor.execute(
                    buildMediaSource = { throw IllegalStateException("boom") },
                    shouldAttachOnMain = { true },
                    attachOnMain = {},
                )

            assertTrue(result is PlaylistPreloadExecutionResult.Failed)
            assertEquals("boom", (result as PlaylistPreloadExecutionResult.Failed).error.message)
        }
}
