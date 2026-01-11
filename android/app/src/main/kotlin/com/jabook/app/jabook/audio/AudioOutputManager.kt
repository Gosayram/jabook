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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio output switching between speaker and earpiece based on proximity sensor.
 *
 * Logic:
 * - When proximity sensor detects "NEAR" (phone at ear):
 *   - Screen turns off (via WakeLock)
 *   - Audio switches to earpiece (MODE_IN_COMMUNICATION)
 * - When proximity sensor detects "FAR" (phone away):
 *   - Screen turns on
 *   - Audio switches back to speaker (MODE_NORMAL)
 *
 * Usage:
 * - Call [startMonitoring] when playback starts
 * - Call [stopMonitoring] when playback pauses/stops or service is destroyed
 */
@Singleton
public class AudioOutputManager
    @Inject
    public constructor(
        // Use @param: as required for value parameters that become fields
        @param:ApplicationContext private val context: Context,
    ) : SensorEventListener {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        // WakeLock to turn off screen when at ear
        private val proximityWakeLock: PowerManager.WakeLock? =
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "JaBook:ProximityScreenOff")
            } else {
                null
            }

        private var isMonitoring = false
        private var isAtEar = false

        /**
         * Starts monitoring the proximity sensor.
         * Should be called when playback starts.
         */
        public fun startMonitoring(...) {
            if (isMonitoring || proximitySensor == null) return

            sensorManager.registerListener(
                this,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL,
            )
            isMonitoring = true
            android.util.Log.d("AudioOutputManager", "Proximity monitoring started")
        }

        /**
         * Stops monitoring the proximity sensor and resets audio settings.
         * Should be called when playback stops/pauses.
         */
        public fun stopMonitoring(...) {
            if (!isMonitoring) return

            sensorManager.unregisterListener(this)
            isMonitoring = false

            // Reset state
            setAudioOutput(false) // Force speaker
            releaseWakeLock()

            android.util.Log.d("AudioOutputManager", "Proximity monitoring stopped")
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return

            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5.0f

            // Standard check: distance < maximum range means something is close.
            // Also check < 5.0cm just in case maxRange is very large.
            val isNear = distance < maxRange && distance < 5.0f

            if (isAtEar != isNear) {
                isAtEar = isNear
                android.util.Log.d("AudioOutputManager", "Proximity changed: $isNear (distance=$distance)")

                if (isNear) {
                    // Phone at ear
                    acquireWakeLock()
                    setAudioOutput(true)
                } else {
                    // Phone away
                    releaseWakeLock()
                    setAudioOutput(false)
                }
            }
        }

        override fun onAccuracyChanged(
            sensor: Sensor?,
            accuracy: Int,
        ) {
            // No-op
        }

        private fun setAudioOutput(toEarpiece: Boolean) {
            if (toEarpiece) {
                // Switch to Earpiece
                // MODE_IN_COMMUNICATION is required to route audio to earpiece on modern Android
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // API 31+ (Android 12+)
                    val devices = audioManager.availableCommunicationDevices
                    val earpiece = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    if (earpiece != null) {
                        try {
                            val result = audioManager.setCommunicationDevice(earpiece)
                            if (!result) {
                                android.util.Log.w("AudioOutputManager", "Failed to set communication device to earpiece")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AudioOutputManager", "Error setting communication device", e)
                        }
                    } else {
                        android.util.Log.w("AudioOutputManager", "No built-in earpiece found")
                    }
                } else {
                    // Deprecated method for older Android versions
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }

                android.util.Log.i("AudioOutputManager", "Switched to EARPIECE")
            } else {
                // Switch to Speaker
                audioManager.mode = AudioManager.MODE_NORMAL

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // API 31+: clearCommunicationDevice() resets to default routing (usually speaker or connected headset)
                    audioManager.clearCommunicationDevice()
                } else {
                    // Deprecated method for older Android versions
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }

                android.util.Log.i("AudioOutputManager", "Switched to SPEAKER")
            }
        }

        private fun acquireWakeLock() {
            if (proximityWakeLock?.isHeld == false) {
                // 4 hours timeout safety
                proximityWakeLock.acquire(4 * 60 * 60 * 1000L)
            }
        }

        private fun releaseWakeLock() {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock.release()
            }
        }
    }
