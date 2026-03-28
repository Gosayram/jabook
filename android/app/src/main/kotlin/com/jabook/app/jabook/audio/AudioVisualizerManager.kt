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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio visualization using Android's Visualizer API.
 *
 * Provides waveform and FFT data for UI visualization.
 * Requires RECORD_AUDIO permission on Android 6.0+.
 */
public class AudioVisualizerManager(
    private val context: Context,
    private val permissionChecker: (Context) -> Boolean = { appContext ->
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    },
    private val visualizerFactory: (Int) -> Visualizer = { sessionId -> Visualizer(sessionId) },
) {
    public companion object {
        private const val TAG = "AudioVisualizerManager"
        private const val CAPTURE_SIZE = 256
    }

    private var visualizer: Visualizer? = null
    private var audioSessionId: Int = 0
    private var lastRequestedAudioSessionId: Int = 0
    private var desiredEnabled: Boolean = true

    private val _waveformData = MutableStateFlow(FloatArray(CAPTURE_SIZE))
    public val waveformData: StateFlow<FloatArray> = _waveformData.asStateFlow()

    private val _fftData = MutableStateFlow(FloatArray(CAPTURE_SIZE / 2))
    public val fftData: StateFlow<FloatArray> = _fftData.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    public val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Initialize visualizer with the player's audio session ID.
     */
    public fun initialize(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            Log.w(TAG, "Invalid audio session id: $audioSessionId, visualizer disabled")
            lastRequestedAudioSessionId = 0
            release()
            return
        }

        lastRequestedAudioSessionId = audioSessionId

        if (this.audioSessionId == audioSessionId && visualizer != null) {
            Log.d(TAG, "Visualizer already initialized with same session")
            if (desiredEnabled && !_isActive.value) {
                setEnabled(enabled = true)
            }
            return
        }

        // Check permission
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, visualizer disabled")
            release(clearRequestedSessionId = false)
            return
        }

        release(clearRequestedSessionId = false)
        this.audioSessionId = audioSessionId

        try {
            visualizer =
                visualizerFactory(audioSessionId).apply {
                    captureSize = CAPTURE_SIZE

                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int,
                            ) {
                                waveform?.let { data ->
                                    // Convert bytes to normalized floats (-1.0 to 1.0)
                                    val floatData =
                                        FloatArray(data.size) { i ->
                                            (data[i].toInt() and 0xFF) / 128f - 1f
                                        }
                                    _waveformData.value = floatData
                                }
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int,
                            ) {
                                fft?.let { data ->
                                    // Convert FFT to magnitudes
                                    val magnitudes =
                                        FloatArray(data.size / 2) { i ->
                                            val real = data[i * 2].toInt()
                                            val imaginary = data[i * 2 + 1].toInt()
                                            kotlin.math.sqrt((real * real + imaginary * imaginary).toFloat()) / 128f
                                        }
                                    _fftData.value = magnitudes
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true, // waveform
                        true, // fft
                    )

                    enabled = desiredEnabled
                    _isActive.value = desiredEnabled
                }
            Log.d(TAG, "Visualizer initialized with session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize visualizer", e)
            release(clearRequestedSessionId = false)
        }
    }

    /**
     * Enable or disable the visualizer.
     */
    public fun setEnabled(enabled: Boolean) {
        desiredEnabled = enabled

        if (enabled && !hasRecordAudioPermission()) {
            Log.w(TAG, "Cannot enable visualizer: RECORD_AUDIO permission not granted")
            release(clearRequestedSessionId = false)
            return
        }

        val activeVisualizer = visualizer
        if (activeVisualizer == null) {
            if (enabled && lastRequestedAudioSessionId > 0) {
                initialize(lastRequestedAudioSessionId)
            }
            if (visualizer == null) {
                _isActive.value = false
            }
            return
        }

        try {
            activeVisualizer.enabled = enabled
            _isActive.value = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set visualizer enabled state", e)
            release(clearRequestedSessionId = false)
        }
    }

    /**
     * Release visualizer resources.
     */
    public fun release() {
        release(clearRequestedSessionId = true)
    }

    private fun release(clearRequestedSessionId: Boolean) {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
            audioSessionId = 0
            if (clearRequestedSessionId) {
                lastRequestedAudioSessionId = 0
            }
            _isActive.value = false
            clearVisualizationData()
            Log.d(TAG, "Visualizer released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release visualizer", e)
            visualizer = null
            audioSessionId = 0
            if (clearRequestedSessionId) {
                lastRequestedAudioSessionId = 0
            }
            _isActive.value = false
            clearVisualizationData()
        }
    }

    private fun clearVisualizationData() {
        _waveformData.value = FloatArray(CAPTURE_SIZE)
        _fftData.value = FloatArray(CAPTURE_SIZE / 2)
    }

    private fun hasRecordAudioPermission(): Boolean = permissionChecker(context)
}
