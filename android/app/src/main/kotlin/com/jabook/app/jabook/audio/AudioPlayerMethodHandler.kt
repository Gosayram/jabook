// Copyright 2025 Jabook Contributors
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel handler for audio player operations.
 *
 * This class handles all method calls from Flutter and delegates
 * them to the AudioPlayerService.
 */
class AudioPlayerMethodHandler(
    private val context: Context
) : MethodChannel.MethodCallHandler {
    
    private var audioService: AudioPlayerService? = null
    private var serviceConnection: ServiceConnection? = null
    private var isServiceBound = false
    
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "initialize" -> {
                    bindService()
                    result.success(true)
                }
                "setPlaylist" -> {
                    val filePaths = call.argument<List<String>>("filePaths") ?: emptyList()
                    val metadata = call.argument<Map<String, String>>("metadata")
                    if (filePaths.isEmpty()) {
                        result.error("INVALID_ARGUMENT", "File paths list cannot be empty", null)
                        return
                    }
                    audioService?.setPlaylist(filePaths, metadata)
                    result.success(true)
                }
            "play" -> {
                audioService?.play()
                result.success(true)
            }
            "pause" -> {
                audioService?.pause()
                result.success(true)
            }
            "stop" -> {
                audioService?.stop()
                result.success(true)
            }
            "seek" -> {
                val positionMs = call.argument<Long>("positionMs") ?: 0L
                audioService?.seekTo(positionMs)
                result.success(true)
            }
            "setSpeed" -> {
                val speed = call.argument<Double>("speed")?.toFloat() ?: 1.0f
                audioService?.setSpeed(speed)
                result.success(true)
            }
            "getPosition" -> {
                val position = audioService?.getCurrentPosition() ?: 0L
                result.success(position)
            }
            "getDuration" -> {
                val duration = audioService?.getDuration() ?: 0L
                result.success(duration)
            }
            "getState" -> {
                val state = audioService?.getPlayerState() ?: emptyMap()
                result.success(state)
            }
            "next" -> {
                audioService?.next()
                result.success(true)
            }
            "previous" -> {
                audioService?.previous()
                result.success(true)
            }
            "seekToTrack" -> {
                val index = call.argument<Int>("index") ?: 0
                audioService?.seekToTrack(index)
                result.success(true)
            }
            "updateMetadata" -> {
                val metadata = call.argument<Map<String, String>>("metadata")
                if (metadata != null) {
                    audioService?.updateMetadata(metadata)
                }
                result.success(true)
            }
            "seekToTrackAndPosition" -> {
                val trackIndex = call.argument<Int>("trackIndex") ?: 0
                val positionMs = call.argument<Long>("positionMs") ?: 0L
                audioService?.seekToTrackAndPosition(trackIndex, positionMs)
                result.success(true)
            }
            "dispose" -> {
                unbindService()
                result.success(true)
            }
            else -> result.notImplemented()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerMethodHandler", "Error handling method call: ${call.method}", e)
            result.error("EXCEPTION", e.message ?: "Unknown error", null)
        }
    }
    
    /**
     * Binds to AudioPlayerService.
     */
    private fun bindService() {
        if (isServiceBound) {
            audioService = AudioPlayerService.getInstance()
            return
        }
        
        val intent = Intent(context, AudioPlayerService::class.java)
        context.startForegroundService(intent)
        
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isServiceBound = true
                audioService = AudioPlayerService.getInstance()
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                isServiceBound = false
                audioService = null
            }
        }
        
        context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        
        // Try to get instance immediately (service might already be running)
        audioService = AudioPlayerService.getInstance()
    }
    
    /**
     * Unbinds from AudioPlayerService.
     */
    private fun unbindService() {
        if (!isServiceBound) return
        
        serviceConnection?.let {
            context.unbindService(it)
        }
        serviceConnection = null
        isServiceBound = false
        audioService = null
    }
}

