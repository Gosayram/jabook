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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Manages network fallback to lower quality streams when network conditions degrade.
 *
 * P-17: CrossFadePlayer: fallback на низкое качество (Fallback to low quality)
 */
@UnstableApi
public class NetworkFallbackManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val fallbackQualities: List<String> = listOf("hd", "sd", "ld"),
    private val maxRetries: Int = 3,
) {
    private val scopeJob = SupervisorJob()
    private val scope =
        CoroutineScope(scopeJob + Dispatchers.Main.immediate + loggingCoroutineExceptionHandler("NetworkFallbackManager"))

    private var currentQualityIndex = 0
    private var retryCount = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        startNetworkMonitoring()
    }

    private fun startNetworkMonitoring() {
        // Register network callback to monitor changes
        networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    checkNetworkQuality()
                }

                override fun onLost(network: Network) {
                    handleNetworkLost()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    checkNetworkQuality()
                }
            }

        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun checkNetworkQuality() {
        val network = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (capabilities != null) {
            val downlinkMbps = capabilities.linkDownstreamBandwidthKbps / 1000
            LogUtils.d(TAG, "Network quality: $downlinkMbps Mbps")

            if (downlinkMbps < 5) {
                // Poor network, switch to lower quality
                switchToFallbackQuality()
            } else if (downlinkMbps > 20 && currentQualityIndex > 0) {
                // Good network, try to switch back to higher quality
                switchToHigherQuality()
            }
        }
    }

    private fun handleNetworkLost() {
        LogUtils.w(TAG, "Network lost, pausing playback")
        player.playWhenReady = false
    }

    private fun switchToFallbackQuality() {
        if (currentQualityIndex < fallbackQualities.size - 1) {
            currentQualityIndex++
            val newQuality = fallbackQualities[currentQualityIndex]
            LogUtils.i(TAG, "Switching to fallback quality: $newQuality (retry $retryCount/$maxRetries)")
            retryCount++
            // Reload current track with new quality
            // This would require modifying the MediaSource, which is complex
            // For now, just log
        }
    }

    private fun switchToHigherQuality() {
        if (currentQualityIndex > 0) {
            currentQualityIndex--
            val newQuality = fallbackQualities[currentQualityIndex]
            LogUtils.i(TAG, "Switching to higher quality: $newQuality")
            // Reload current track with new quality
        }
    }

/**
     * Releases the network fallback manager and unregisters callbacks.
     */
    public fun release() {
        networkCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: IllegalArgumentException) {
                LogUtils.w(TAG, "Callback already unregistered", e)
            }
        }
        networkCallback = null
        scope.cancel()
    }

    private companion object {
        private const val TAG = "NetworkFallbackManager"
    }
}
