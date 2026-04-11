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
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.jabook.app.jabook.util.LogUtils

/**
 * Monitors audio output device routing changes (BP-13.3).
 *
 * Uses [AudioManager.getDevices] and [AudioDeviceCallback] to detect
 * the current output type (speaker/headphone/Bluetooth/USB).
 *
 * When the output switches to speaker (public scenario), auto-resume
 * should be suppressed to prevent accidental playback in public.
 */
internal class AudioOutputDeviceMonitor(
    context: Context,
) {
    companion object {
        private const val TAG = "AudioOutputDeviceMonitor"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Current output device type.
     */
    var currentOutputType: OutputType = detectCurrentOutputType()
        private set

    /**
     * Whether the current output is a public-facing speaker.
     * When true, auto-resume should be suppressed.
     */
    val isPublicOutput: Boolean
        get() = currentOutputType == OutputType.SPEAKER

    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val newType = detectCurrentOutputType()
                if (newType != currentOutputType) {
                    LogUtils.d(TAG, "Audio device added, output changed: $currentOutputType → $newType")
                    currentOutputType = newType
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val newType = detectCurrentOutputType()
                if (newType != currentOutputType) {
                    LogUtils.d(TAG, "Audio device removed, output changed: $currentOutputType → $newType")
                    currentOutputType = newType
                }
            }
        }

    private var isRegistered = false

    /**
     * Starts monitoring audio device changes.
     */
    fun register() {
        if (isRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            isRegistered = true
            LogUtils.d(TAG, "Registered audio device monitor (current: $currentOutputType)")
        }
    }

    /**
     * Stops monitoring audio device changes.
     */
    fun unregister() {
        if (!isRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            isRegistered = false
            LogUtils.d(TAG, "Unregistered audio device monitor")
        }
    }

    /**
     * Detects the current primary output device type.
     */
    private fun detectCurrentOutputType(): OutputType {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return OutputType.SPEAKER // Default fallback
        }

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (devices.isNullOrEmpty()) {
            return OutputType.SPEAKER
        }

        // Priority: Bluetooth > USB > Wired > Speaker
        // Check for BT first (A2DP or hearing aid)
        val hasBt = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_HEARING_AID ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }
        if (hasBt) return OutputType.BLUETOOTH

        // Check for USB audio
        val hasUsb = devices.any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (hasUsb) return OutputType.USB

        // Check for wired headset/headphones
        val hasWired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL
        }
        if (hasWired) return OutputType.WIRED_HEADPHONE

        return OutputType.SPEAKER
    }

    /**
     * Audio output device types.
     */
    enum class OutputType {
        /** Built-in speaker — public, auto-resume should be suppressed. */
        SPEAKER,

        /** Wired headphones or headset — private. */
        WIRED_HEADPHONE,

        /** Bluetooth audio device (A2DP/SCO/LE). */
        BLUETOOTH,

        /** USB audio device. */
        USB,
    }
}