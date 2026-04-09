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
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Legacy phone state listener for API 30 (Android 11).
 * Isolated to keep deprecated API separate from modern implementation.
 */
@Suppress("DEPRECATION")
private object LegacyPhoneStateListener {
    public fun create(onStateChanged: (Int, String?) -> Unit): android.telephony.PhoneStateListener =
        object : android.telephony.PhoneStateListener() {
            @Suppress("OVERRIDE_DEPRECATION") // onCallStateChanged is deprecated
            override fun onCallStateChanged(
                state: Int,
                phoneNumber: String?,
            ) {
                onStateChanged(state, phoneNumber)
            }
        }
}

/**
 * Listens for phone call state changes and automatically resumes playback after a call ends.
 *
 * This listener tracks phone call state and automatically resumes audio playback when:
 * - A call ends (CALL_STATE_IDLE after CALL_STATE_OFFHOOK)
 * - The call was active (we were playing before the call)
 *
 * This improves UX by automatically resuming audiobook playback after phone calls,
 * which is especially useful for users who listen while driving or doing other activities.
 *
 * Note: Requires READ_PHONE_STATE permission in AndroidManifest.xml
 *
 * Uses modern TelephonyCallback API (API 31+) with fallback to PhoneStateListener (API 30).
 */
public class PhoneCallListener(
    private val context: Context,
    private val getActivePlayer: () -> ExoPlayer,
    private val wasPlayingBeforeCall: () -> Boolean,
    private val setWasPlayingBeforeCall: (Boolean) -> Unit,
) {
    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("PhoneCallListener"),
        )

    // Modern API (API 31+)
    private var telephonyCallback: TelephonyCallback? = null

    // Legacy API (API 30 fallback) - isolated in LegacyPhoneStateListener object
    private var phoneStateListener: Any? = null

    private var isRegistered = false

    // Track call state to detect when call ends
    private var wasInCall = false

    /**
     * Starts listening for phone call state changes.
     * Should be called when playback starts or service is created.
     */
    public fun startListening() {
        if (isRegistered) {
            android.util.Log.w("PhoneCallListener", "Already listening for phone calls")
            return
        }

        if (telephonyManager == null) {
            android.util.Log.w("PhoneCallListener", "TelephonyManager not available, cannot listen for phone calls")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use modern TelephonyCallback API (API 31+)
                telephonyCallback =
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallStateChange(state, null)
                        }
                    }

                // Register callback using Executor
                telephonyManager.registerTelephonyCallback(
                    ContextCompat.getMainExecutor(context),
                    telephonyCallback!!,
                )
                android.util.Log.i("PhoneCallListener", "Started listening for phone calls (TelephonyCallback API 31+)")
            } else {
                // Fallback to PhoneStateListener for API 30
                // Legacy API is isolated in LegacyPhoneStateListener object
                phoneStateListener =
                    LegacyPhoneStateListener.create { state, phoneNumber ->
                        handleCallStateChange(state, phoneNumber)
                    }

                // Register listener for CALL_STATE changes
                @Suppress("DEPRECATION") // listen() and LISTEN_CALL_STATE are deprecated
                telephonyManager.listen(
                    phoneStateListener as android.telephony.PhoneStateListener,
                    android.telephony.PhoneStateListener.LISTEN_CALL_STATE,
                )
                android.util.Log.i("PhoneCallListener", "Started listening for phone calls (PhoneStateListener API 30)")
            }
            isRegistered = true
        } catch (e: SecurityException) {
            android.util.Log.e("PhoneCallListener", "Permission denied: READ_PHONE_STATE", e)
        } catch (e: Exception) {
            android.util.Log.e("PhoneCallListener", "Failed to start listening for phone calls", e)
        }
    }

    /**
     * Stops listening for phone call state changes.
     * Should be called when playback stops or service is destroyed.
     */
    public fun stopListening() {
        if (!isRegistered) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Unregister modern TelephonyCallback (API 31+)
                telephonyCallback?.let { callback ->
                    telephonyManager?.unregisterTelephonyCallback(callback)
                }
                telephonyCallback = null
            } else {
                // Unregister legacy PhoneStateListener (API 30)
                @Suppress("DEPRECATION") // listen() and LISTEN_NONE are deprecated
                telephonyManager?.listen(
                    phoneStateListener as? android.telephony.PhoneStateListener,
                    android.telephony.PhoneStateListener.LISTEN_NONE,
                )
                phoneStateListener = null
            }
            isRegistered = false
            wasInCall = false

            android.util.Log.i("PhoneCallListener", "Stopped listening for phone call state changes")
        } catch (e: Exception) {
            android.util.Log.e("PhoneCallListener", "Failed to stop listening for phone calls", e)
        }
    }

    /**
     * Handles phone call state changes.
     *
     * States:
     * - CALL_STATE_IDLE: No call active
     * - CALL_STATE_RINGING: Incoming call is ringing
     * - CALL_STATE_OFFHOOK: Call is active (answered or outgoing)
     */
    private fun handleCallStateChange(
        state: Int,
        phoneNumber: String?,
    ) {
        val stateName =
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> "UNKNOWN($state)"
            }

        android.util.Log.d(
            "PhoneCallListener",
            "Call state changed: $stateName (phoneNumber=${phoneNumber?.take(4)}...)",
        )

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming call - check if we're playing and save state
                val player = getActivePlayer()
                if (player.isPlaying) {
                    setWasPlayingBeforeCall(true)
                    android.util.Log.i("PhoneCallListener", "Incoming call detected, saving playback state")
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered or outgoing call started
                wasInCall = true
                val player = getActivePlayer()
                if (player.isPlaying) {
                    setWasPlayingBeforeCall(true)
                    android.util.Log.i("PhoneCallListener", "Call active, saving playback state")
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended - resume playback if we were playing before
                if (wasInCall && wasPlayingBeforeCall()) {
                    wasInCall = false
                    android.util.Log.i("PhoneCallListener", "Call ended, attempting to resume playback")

                    // Delay resume slightly to ensure audio focus is regained
                    scope.launch {
                        delay(500L) // Small delay to ensure audio focus is ready

                        try {
                            val player = getActivePlayer()

                            // Check if player is still valid and not completed
                            if (player.playbackState == Player.STATE_READY ||
                                player.playbackState == Player.STATE_BUFFERING
                            ) {
                                // Resume playback
                                player.playWhenReady = true
                                setWasPlayingBeforeCall(false)

                                android.util.Log.i(
                                    "PhoneCallListener",
                                    "Playback resumed after call ended",
                                )
                            } else {
                                android.util.Log.w(
                                    "PhoneCallListener",
                                    "Cannot resume playback: player state=${player.playbackState}",
                                )
                                setWasPlayingBeforeCall(false)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PhoneCallListener", "Failed to resume playback after call", e)
                            setWasPlayingBeforeCall(false)
                        }
                    }
                } else {
                    // Call ended but we weren't playing before, or call wasn't active
                    wasInCall = false
                    if (!wasPlayingBeforeCall()) {
                        android.util.Log.d("PhoneCallListener", "Call ended, but playback was not active before call")
                    }
                }
            }
        }
    }
}
