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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles post-initialization orchestration for [AudioPlayerService].
 *
 * This keeps [AudioPlayerServiceInitializer] focused on component wiring while
 * post-init concerns (restore/apply/runtime observers) live in one place.
 */
internal class AudioPlayerPostInitCoordinator(
    private val service: AudioPlayerService,
) {
    fun run() {
        restorePlaybackSpeed()
        setupNotificationProvider()
        setupPlayerNotificationManagerFallback()
        setupAudioOutputManager()
        service.playbackEnhancerService.initialize()
        initializeVisualizer()
    }

    private fun restorePlaybackSpeed() {
        service.playerServiceScope.launch {
            try {
                val savedSpeed = service.audioPreferences.playbackSpeed.first()
                withContext(Dispatchers.Main) {
                    LogUtils.d("AudioPlayerService", "Restoring playback speed: ${savedSpeed}x")
                    service.exoPlayer.setPlaybackSpeed(savedSpeed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Failed to restore playback speed", e)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun setupNotificationProvider() {
        if (service.mediaLibrarySession != null) {
            service.setNotificationProvider(AudioPlayerNotificationProvider(service))
            LogUtils.i("AudioPlayerService", "MediaNotificationProvider set for MediaLibrarySession")
        } else {
            LogUtils.w("AudioPlayerService", "MediaLibrarySession is null, cannot set MediaNotificationProvider")
        }
    }

    private fun setupPlayerNotificationManagerFallback() {
        if (service.mediaLibrarySession == null) {
            LogUtils.w("AudioPlayerService", "MediaLibrarySession not available, using PlayerNotificationManager as fallback")
            service.setupPlayerNotificationManager()
        }
    }

    private fun setupAudioOutputManager() {
        if (service.exoPlayer.isPlaying) {
            service.audioOutputManager.startMonitoring()
        }

        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        service.audioOutputManager.startMonitoring()
                    } else {
                        service.audioOutputManager.stopMonitoring()
                    }
                }
            }
        service.exoPlayer.addListener(listener)
    }

    private fun initializeVisualizer() {
        service.audioVisualizerManager?.release()
        service.audioVisualizerManager = AudioVisualizerManager(service)
        service.visualizerBridgeJob?.cancel()
        service.visualizerBridgeJob =
            service.playerServiceScope.launch {
                service.audioVisualizerManager
                    ?.waveformData
                    ?.collect { waveform ->
                        service.audioVisualizerStateBridge.updateWaveform(waveform)
                    }
            }
    }
}
