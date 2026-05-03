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

import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TrackExistencePolicyTest {
    @Test
    fun `exists returns true when media item is present`() {
        val player = mock<ExoPlayer>()
        whenever(player.getMediaItemAt(any())).thenReturn(mock())

        assertTrue(TrackExistencePolicy.exists(player, 2))
    }

    @Test
    fun `exists returns false for index out of bounds`() {
        val player = mock<ExoPlayer>()
        whenever(player.getMediaItemAt(any())).doThrow(IndexOutOfBoundsException("missing"))

        assertFalse(TrackExistencePolicy.exists(player, 99))
    }

    @Test
    fun `exists returns false for unexpected player exceptions`() {
        val player = mock<ExoPlayer>()
        whenever(player.getMediaItemAt(any())).doThrow(IllegalStateException("player error"))

        assertFalse(TrackExistencePolicy.exists(player, 1))
    }
}
