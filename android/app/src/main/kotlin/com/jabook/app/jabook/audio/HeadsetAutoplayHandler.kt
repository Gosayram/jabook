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

import android.media.AudioDeviceInfo
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles headset auto-play with device-type-specific delays for A2DP negotiation.
 *
 * P-25: BLE headsets require 300–800ms to negotiate A2DP profile after connection.
 * If playback starts immediately, the first seconds play through the device speaker
 * before switching to the headset. This handler adds appropriate delays by device type.
 *
 * Usage:
 * ```
 * handler.onHeadsetConnected(deviceInfo, scope) {
 *     player.play()
 * }
 * ```
 *
 * @param isDeviceConnected Check if the audio device is still connected
 */
internal class HeadsetAutoplayHandler(
    private val isDeviceConnected: (AudioDeviceInfo) -> Boolean = { true },
) {
    /**
     * Called when a headset is connected. Delays auto-play based on device type.
     *
     * @param deviceInfo The connected audio device
     * @param scope Coroutine scope for the delay
     * @param onReady Callback to invoke after delay (if device still connected)
     */
    fun onHeadsetConnected(
        deviceInfo: AudioDeviceInfo,
        scope: CoroutineScope,
        onReady: () -> Unit,
    ) {
        val delayMs =
            when (deviceInfo.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BLE_DELAY_MS
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> BLE_DELAY_MS
                AudioDeviceInfo.TYPE_USB_HEADSET -> USB_DELAY_MS
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                -> 0L

                else -> DEFAULT_DELAY_MS
            }

        if (delayMs == 0L) {
            LogUtils.d(TAG, "Wired headset — immediate autoplay")
            onReady()
            return
        }

        LogUtils.d(TAG, "${deviceTypeLabel(deviceInfo.type)} — delaying autoplay ${delayMs}ms for A2DP negotiation")
        scope.launch {
            delay(delayMs)
            if (isDeviceConnected(deviceInfo)) {
                LogUtils.d(TAG, "Device still connected after delay — starting autoplay")
                onReady()
            } else {
                LogUtils.d(TAG, "Device disconnected during delay — skipping autoplay")
            }
        }
    }

    private fun deviceTypeLabel(type: Int): String =
        when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            else -> "Audio Device (type=$type)"
        }

    companion object {
        internal const val BLE_DELAY_MS = 600L
        internal const val USB_DELAY_MS = 200L
        internal const val DEFAULT_DELAY_MS = 300L
        private const val TAG = "HeadsetAutoplay"
    }
}
