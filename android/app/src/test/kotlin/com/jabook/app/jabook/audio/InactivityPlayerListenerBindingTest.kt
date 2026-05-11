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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class InactivityPlayerListenerBindingTest {
    @Test
    fun `attach adds provided listener to player`() {
        val player = mock<ExoPlayer>()
        val listener = mock<Player.Listener>()
        val binding = InactivityPlayerListenerBinding(player = player, listener = listener)

        binding.attach()

        verify(player).addListener(listener)
    }

    @Test
    fun `detach removes provided listener from player`() {
        val player = mock<ExoPlayer>()
        val listener = mock<Player.Listener>()
        val binding = InactivityPlayerListenerBinding(player = player, listener = listener)

        binding.detach()

        verify(player).removeListener(listener)
    }

    @Test
    fun `attach then detach keeps listener lifecycle order`() {
        val player = mock<ExoPlayer>()
        val listener = mock<Player.Listener>()
        val binding = InactivityPlayerListenerBinding(player = player, listener = listener)

        binding.attach()
        binding.detach()

        inOrder(player) {
            verify(player).addListener(listener)
            verify(player).removeListener(listener)
        }
    }
}
