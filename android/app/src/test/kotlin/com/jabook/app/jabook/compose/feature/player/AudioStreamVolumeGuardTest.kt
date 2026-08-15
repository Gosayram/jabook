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

package com.jabook.app.jabook.compose.feature.player

import android.media.AudioManager
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioStreamVolumeGuardTest {
    private val audioManager: AudioManager = mock()

    @Test
    fun `does not adjust a fixed-volume output`() {
        whenever(audioManager.isVolumeFixed).thenReturn(true)

        val adjusted = audioManager.adjustMusicVolumeIfMutable(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)

        org.junit.Assert.assertFalse(adjusted)
        verify(audioManager, never()).adjustStreamVolume(any(), any(), any())
    }

    @Test
    fun `adjusts a mutable output`() {
        whenever(audioManager.isVolumeFixed).thenReturn(false)

        val adjusted = audioManager.adjustMusicVolumeIfMutable(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)

        org.junit.Assert.assertTrue(adjusted)
        verify(audioManager).adjustStreamVolume(
            eq(AudioManager.STREAM_MUSIC),
            eq(AudioManager.ADJUST_LOWER),
            eq(AudioManager.FLAG_SHOW_UI),
        )
    }
}
