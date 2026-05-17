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

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils

/**
 * Creates [CrossFadePlayer] and binds MediaSession player switching callbacks.
 */
internal object AudioCrossFadeSessionBinder {
    @OptIn(UnstableApi::class)
    fun bind(service: AudioPlayerService) {
        if (service.crossFadePlayer != null) {
            return
        }
        service.crossFadePlayer =
            CrossFadePlayer(service) { context, handleAudioFocus ->
                ExoPlayer
                    .Builder(context)
                    .setRenderersFactory(DefaultRenderersFactory(context))
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .setHandleAudioBecomingNoisy(true)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        handleAudioFocus,
                    ).build()
            }
        service.crossFadePlayer?.onPlayerChanged = { newPlayer ->
            try {
                service.mediaLibrarySession?.let { session ->
                    session.player = newPlayer
                    LogUtils.d(
                        "AudioPlayerService",
                        "MediaSession player updated after crossfade: ${newPlayer.javaClass.simpleName}",
                    )
                } ?: LogUtils.w(
                    "AudioPlayerService",
                    "MediaLibrarySession is null, cannot update player after crossfade",
                )
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error updating MediaSession player after crossfade", e)
            }
        }
    }
}
