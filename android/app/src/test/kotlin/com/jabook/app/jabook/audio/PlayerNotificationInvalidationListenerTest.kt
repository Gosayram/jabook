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

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerNotificationInvalidationListenerTest {
    @Test
    fun `media metadata changed is routed as debounced signal`() {
        val sink = RecordingNotificationInvalidationSignalSink()
        val listener = PlayerNotificationInvalidationListener(signals = sink)

        listener.onMediaMetadataChanged(MediaMetadata.Builder().build())

        assertEquals(
            listOf(
                Signal(
                    type = SignalType.DEBOUNCED,
                    event = PlayerNotificationInvalidationListener.EVENT_MEDIA_METADATA_CHANGED,
                ),
            ),
            sink.signals,
        )
    }

    @Test
    fun `media item transition is routed as debounced signal`() {
        val sink = RecordingNotificationInvalidationSignalSink()
        val listener = PlayerNotificationInvalidationListener(signals = sink)

        listener.onMediaItemTransition(mediaItem = null, reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertEquals(
            listOf(
                Signal(
                    type = SignalType.DEBOUNCED,
                    event = PlayerNotificationInvalidationListener.EVENT_MEDIA_ITEM_TRANSITION,
                ),
            ),
            sink.signals,
        )
    }

    @Test
    fun `playback ready state is routed as immediate signal`() {
        val sink = RecordingNotificationInvalidationSignalSink()
        val listener = PlayerNotificationInvalidationListener(signals = sink)

        listener.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals(
            listOf(
                Signal(
                    type = SignalType.IMMEDIATE,
                    event = PlayerNotificationInvalidationListener.EVENT_PLAYBACK_STATE_READY,
                ),
            ),
            sink.signals,
        )
    }

    @Test
    fun `non-ready playback state does not emit signal`() {
        val sink = RecordingNotificationInvalidationSignalSink()
        val listener = PlayerNotificationInvalidationListener(signals = sink)

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)

        assertEquals(emptyList<Signal>(), sink.signals)
    }

    @Test
    fun `is playing changed is routed as immediate signal`() {
        val sink = RecordingNotificationInvalidationSignalSink()
        val listener = PlayerNotificationInvalidationListener(signals = sink)

        listener.onIsPlayingChanged(true)

        assertEquals(
            listOf(
                Signal(
                    type = SignalType.IMMEDIATE,
                    event = PlayerNotificationInvalidationListener.isPlayingChangedEvent(isPlaying = true),
                ),
            ),
            sink.signals,
        )
    }

    private class RecordingNotificationInvalidationSignalSink : PlayerNotificationInvalidationSignalSink {
        val signals = mutableListOf<Signal>()

        override fun onDebouncedSignal(event: String) {
            signals += Signal(type = SignalType.DEBOUNCED, event = event)
        }

        override fun onImmediateSignal(event: String) {
            signals += Signal(type = SignalType.IMMEDIATE, event = event)
        }
    }

    private enum class SignalType {
        DEBOUNCED,
        IMMEDIATE,
    }

    private data class Signal(
        val type: SignalType,
        val event: String,
    )
}
