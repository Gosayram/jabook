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

import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.cancelChildren

/**
 * Handles cleanup and release of all runtime components in [AudioPlayerService].
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates all resource release logic in one place for clarity and correctness.
 *
 * Responsibilities:
 * - Stop and release all managers (sleep timer, inactivity, playback timer, etc.)
 * - Release media players (ExoPlayer, CrossFadePlayer)
 * - Release media sessions and controllers
 * - Cancel coroutine scope children
 * - Reset initialization flag
 */
internal class AudioServiceReleaseHandler(
    private val getService: () -> AudioPlayerService,
) {
    /**
     * Checks for existing runtime components and performs pre-init cleanup.
     * Called at the start of onCreate() to prevent resource leaks when Android
     * calls onCreate() multiple times without onDestroy().
     */
    fun cleanupExistingComponents() {
        val service = getService()
        val hasExistingComponents =
            service.mediaLibrarySession != null ||
                service.serviceMediaController != null ||
                service.crossFadePlayer != null ||
                service.audioVisualizerManager != null ||
                service.visualizerBridgeJob != null
        if (hasExistingComponents) {
            LogUtils.w(
                "AudioPlayerService",
                "onCreate() called with existing runtime components, performing pre-init cleanup",
            )
        }
        releaseRuntimeComponents(cancelServiceScopeChildren = true)
        if (hasExistingComponents) {
            LogUtils.i("AudioPlayerService", "Existing components cleaned up")
        }
    }

    /**
     * Releases all runtime components managed by the service.
     *
     * @param cancelServiceScopeChildren Whether to cancel coroutine scope children.
     * Should be `true` during onDestroy and pre-init cleanup, `false` if scope
     * is still needed.
     */
    fun releaseRuntimeComponents(cancelServiceScopeChildren: Boolean) {
        val service = getService()

        if (service.isPlaybackPositionRepositoryInitialized()) {
            service.periodicPositionSaver.stop()
        }
        if (cancelServiceScopeChildren) {
            service.playerServiceScope.coroutineContext.cancelChildren()
        }

        service.playerNotificationManager?.setPlayer(null)
        service.playerNotificationManager = null

        if (service.isAudioOutputManagerInitialized()) {
            service.audioOutputManager.stopMonitoring()
        }
        if (service.isPlaybackEnhancerServiceInitialized()) {
            service.playbackEnhancerService.release()
        }

        service.sleepTimerManager?.release()
        service.sleepTimerManager = null

        service.inactivityTimer?.release()
        service.inactivityTimer = null

        service.playbackTimer?.release()
        service.playbackTimer = null
        service.crossfadeHandler?.stopMonitoring()
        service.crossfadeHandler = null
        service.crossFadePlayer?.release()
        service.crossFadePlayer = null

        service.audioVisualizerManager?.release()
        service.audioVisualizerManager = null
        service.visualizerBridgeJob?.cancel()
        service.visualizerBridgeJob = null
        if (service.isAudioVisualizerStateBridgeInitialized()) {
            service.audioVisualizerStateBridge.reset()
        }

        service.phoneCallListener?.stopListening()
        service.phoneCallListener = null

        service.headsetAutoplayHandler?.stopListening()
        service.headsetAutoplayHandler = null

        // BP-13.3: Unregister audio output device monitor
        service.audioOutputDeviceMonitor?.unregister()
        service.audioOutputDeviceMonitor = null

        service.serviceMediaController?.release()
        service.serviceMediaController = null

        service.mediaSessionManager?.release()
        service.mediaSessionManager = null

        service.mediaLibrarySession?.release()
        service.mediaLibrarySession = null
        service.mediaSession = null

        service.mediaSessionLayoutHelper.release()

        service.playerConfigurator?.release()

        service.isFullyInitializedFlag = false
    }

    /**
     * Stops playback and releases player resources.
     * Called for explicit stop (e.g., from Stop button in notification).
     */
    public fun stopAndRelease() {
        val service = getService()
        val player = service.getActivePlayer()
        player.stop()
        player.clearMediaItems()
        service.playbackTimer?.stopTimer()
        service.inactivityTimer?.stopTimer()

        // Release MediaSession
        service.mediaSessionManager?.release()
        service.mediaSession = null

        LogUtils.d("AudioServiceReleaseHandler", "Player stopped and resources released")
    }
}
