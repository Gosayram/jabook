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

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.jabook.app.jabook.util.LogUtils

/**
 * Handles headset and Bluetooth audio device connection/disconnection events.
 *
 * BP-13.2: Bluetooth A2DP reconnect guard.
 * - On BT disconnect: saves position and pauses playback immediately.
 * - On BT reconnect: triggers resume suggestion instead of auto-playing.
 *   UI layer should show "Resume?" snackbar.
 *
 * Also handles wired headset plug events with auto-resume.
 */
public class HeadsetAutoplayHandler(
    private val context: Context,
    private val onHeadsetConnected: () -> Unit,
    private val onHeadsetDisconnected: (() -> Unit)? = null,
) {
    public companion object {
        private const val TAG = "HeadsetAutoplayHandler"
    }

    /**
     * Whether playback was active before BT disconnect.
     * Used to decide if a resume suggestion should be shown on reconnect.
     */
    public var wasPlayingBeforeBtDisconnect: Boolean = false
        private set

    /**
     * Whether the last disconnect was a Bluetooth device.
     * Used to distinguish BT reconnect from wired headset plug.
     */
    public var lastDisconnectWasBluetooth: Boolean = false
        private set

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 1) { // Plugged
                            LogUtils.d(TAG, "Wired headset connected, triggering autoplay")
                            lastDisconnectWasBluetooth = false
                            onHeadsetConnected()
                        } else if (state == 0) { // Unplugged
                            LogUtils.d(TAG, "Wired headset disconnected")
                            onHeadsetDisconnected?.invoke()
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        LogUtils.d(TAG, "Bluetooth device connected")
                        if (lastDisconnectWasBluetooth && wasPlayingBeforeBtDisconnect) {
                            // BP-13.2: Don't auto-resume, let UI show "Resume?" snackbar
                            LogUtils.d(TAG, "BT reconnect after active playback — suggesting resume")
                            onHeadsetConnected()
                        } else {
                            LogUtils.d(TAG, "BT connected but no prior active playback — ignoring")
                        }
                        lastDisconnectWasBluetooth = false
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        LogUtils.d(TAG, "Bluetooth device disconnected: ${device?.name ?: "unknown device"}")
                        lastDisconnectWasBluetooth = true
                        // BP-13.2: Pause and save position on BT disconnect
                        onHeadsetDisconnected?.invoke()
                    }
                }
            }
        }

    private var isRegistered = false

    /**
     * Records that playback was active before BT disconnect.
     * Called by the service when pausing due to BT disconnect.
     */
    public fun recordWasPlaying(wasPlaying: Boolean) {
        wasPlayingBeforeBtDisconnect = wasPlaying
    }

    /**
     * Starts listening for headset connection events.
     */
    public fun startListening() {
        if (isRegistered) return

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        context.registerReceiver(receiver, filter)
        isRegistered = true
        LogUtils.d(TAG, "HeadsetAutoplayHandler started (with BT disconnect guard)")
    }

    /**
     * Stops listening for events.
     */
    public fun stopListening() {
        if (!isRegistered) return

        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            LogUtils.w(TAG, "Error unregistering receiver", e)
        }
        isRegistered = false
        LogUtils.d(TAG, "HeadsetAutoplayHandler stopped")
    }
}