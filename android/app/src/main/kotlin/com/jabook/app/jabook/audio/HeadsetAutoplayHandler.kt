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

/**
 * Automatically resumes playback when a headset or Bluetooth device is connected.
 *
 * Features:
 * - Listens for wired headset plug events (ACTION_HEADSET_PLUG)
 * - Listens for Bluetooth device connection events (ACTION_ACL_CONNECTED)
 * - Resumes playback if it was paused
 */
public class HeadsetAutoplayHandler(
    private val context: Context,
    private val onHeadsetConnected: () -> Unit,
) {
    public companion object {
        private const val TAG = "HeadsetAutoplayHandler"
    }

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
                            Log.d(TAG, "Wired headset connected, triggering autoplay")
                            onHeadsetConnected()
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // Check if it's likely an audio device (this is a rough check,
                        // ideally we'd check device class, but simplified for now)
                        Log.d(TAG, "Bluetooth device connected, triggering autoplay")
                        onHeadsetConnected()
                    }
                }
            }
        }

    private var isRegistered = false

    /**
     * Starts listening for headset connection events.
     */
    public fun startListening() {
        if (isRegistered) return

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            }
        context.registerReceiver(receiver, filter)
        isRegistered = true
        Log.d(TAG, "HeadsetAutoplayHandler started")
    }

    /**
     * Stops listening for events.
     */
    public fun stopListening() {
        if (!isRegistered) return

        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver", e)
        }
        isRegistered = false
        Log.d(TAG, "HeadsetAutoplayHandler stopped")
    }
}
