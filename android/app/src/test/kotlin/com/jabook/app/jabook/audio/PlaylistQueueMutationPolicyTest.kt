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

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistQueueMutationPolicyTest {
    @Test
    fun `add inserts path and shifts active index when inserted before current`() {
        val result =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("a.mp3", "b.mp3", "c.mp3"),
                currentIndex = 1,
                operation = PlaylistQueueOperation.Add(path = "x.mp3", index = 1),
            )

        assertEquals(listOf("a.mp3", "x.mp3", "b.mp3", "c.mp3"), result.paths)
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `remove adjusts index and handles current removal`() {
        val result =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("a.mp3", "b.mp3", "c.mp3"),
                currentIndex = 2,
                operation = PlaylistQueueOperation.Remove(index = 2),
            )

        assertEquals(listOf("a.mp3", "b.mp3"), result.paths)
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `move reorders queue and rebinds active index`() {
        val result =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("a.mp3", "b.mp3", "c.mp3", "d.mp3"),
                currentIndex = 1,
                operation = PlaylistQueueOperation.Move(fromIndex = 1, toIndex = 3),
            )

        assertEquals(listOf("a.mp3", "c.mp3", "d.mp3", "b.mp3"), result.paths)
        assertEquals(3, result.currentIndex)
    }

    @Test
    fun `replace clamps play index and supports explicit playAt`() {
        val implicit =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("a.mp3", "b.mp3"),
                currentIndex = 1,
                operation = PlaylistQueueOperation.Replace(paths = listOf("x.mp3")),
            )
        val explicit =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("a.mp3", "b.mp3"),
                currentIndex = 1,
                operation = PlaylistQueueOperation.Replace(paths = listOf("x.mp3", "y.mp3"), playAtIndex = 1),
            )

        assertEquals(0, implicit.currentIndex)
        assertEquals(1, explicit.currentIndex)
    }

    @Test
    fun `move no-op when indices are equal`() {
        val result =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("a.mp3", "b.mp3", "c.mp3"),
                currentIndex = 1,
                operation = PlaylistQueueOperation.Move(fromIndex = 1, toIndex = 1),
            )

        assertEquals(listOf("a.mp3", "b.mp3", "c.mp3"), result.paths)
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `remove last remaining item resets queue index to zero`() {
        val result =
            PlaylistQueueMutationPolicy.apply(
                currentPaths = listOf("only.mp3"),
                currentIndex = 0,
                operation = PlaylistQueueOperation.Remove(index = 0),
            )

        assertEquals(emptyList<String>(), result.paths)
        assertEquals(0, result.currentIndex)
    }
}
