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

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/**
 * Maps player callbacks to notification invalidation signals.
 */
internal class PlayerNotificationInvalidationListener(
    private val signals: PlayerNotificationInvalidationSignalSink,
) : Player.Listener {
    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        signals.onDebouncedSignal(event = EVENT_MEDIA_METADATA_CHANGED)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        signals.onDebouncedSignal(event = EVENT_MEDIA_ITEM_TRANSITION)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            signals.onImmediateSignal(event = EVENT_PLAYBACK_STATE_READY)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        signals.onImmediateSignal(event = isPlayingChangedEvent(isPlaying))
    }

    internal companion object {
        const val EVENT_MEDIA_METADATA_CHANGED: String = "onMediaMetadataChanged"
        const val EVENT_MEDIA_ITEM_TRANSITION: String = "onMediaItemTransition"
        const val EVENT_PLAYBACK_STATE_READY: String = "onPlaybackStateChanged: READY"

        fun isPlayingChangedEvent(isPlaying: Boolean): String = "onIsPlayingChanged: $isPlaying"
    }
}
