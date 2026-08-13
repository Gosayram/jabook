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

package com.jabook.app.jabook.audio.processors

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

public enum class AudioOutputType {
    SPEAKER,
    WIRED,
    BLUETOOTH,
    UNKNOWN,
}

@RequiresApi(Build.VERSION_CODES.M)
public fun detectAudioOutputType(context: Context): AudioOutputType {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    for (device in devices) {
        if (device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        ) {
            return AudioOutputType.BLUETOOTH
        }
        if (device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        ) {
            return AudioOutputType.WIRED
        }
    }
    return AudioOutputType.SPEAKER
}

/**
 * Policy that recommends an EQ preset based on listening context.
 * One recommendation per session — caller tracks the flag.
 *
 * TASK-EQ-09: Context-aware auto-preset recommendation
 */
public class EqContextRecommendationPolicy(
    private val context: Context,
) {
    public fun recommend(
        hourOfDay: Int,
        audioOutputType: AudioOutputType,
        bluetoothDeviceClass: Int?,
    ): EqualizerPreset? {
        // ponytail: car detection requires BluetoothDevice class check — add when
        // BluetoothDevice.EXTRA_CLASS is available and car audio devices are detected
        if (hourOfDay in 22..23 || hourOfDay in 0..6) {
            return EqualizerPreset.NIGHT_LISTENING
        }
        if (audioOutputType == AudioOutputType.BLUETOOTH) {
            return EqualizerPreset.HEADPHONES_BUDGET
        }
        if (audioOutputType == AudioOutputType.SPEAKER) {
            return EqualizerPreset.SPEAKER_PHONE
        }
        return null
    }

    public companion object {
        public fun detectAudioOutputType(context: Context): AudioOutputType =
            com.jabook.app.jabook.audio.processors
                .detectAudioOutputType(context)
    }
}
