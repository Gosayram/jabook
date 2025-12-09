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

package com.jabook.app.jabook.audio.bridge

import com.jabook.app.jabook.audio.core.model.PlaybackState
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handler for EventChannel to send events to Flutter.
 *
 * Converts Kotlin Flow to EventChannel events for reactive updates.
 */
class EventChannelHandler(
    private val coroutineScope: CoroutineScope,
) {
    private var eventSink: EventChannel.EventSink? = null
    private var flowJob: Job? = null

    /**
     * Sends playback state updates to Flutter.
     */
    fun sendPlaybackState(playbackState: PlaybackState) {
        eventSink?.success(
            mapOf(
                "isPlaying" to playbackState.isPlaying,
                "currentPosition" to playbackState.currentPosition,
                "duration" to playbackState.duration,
                "currentTrackIndex" to playbackState.currentTrackIndex,
                "playbackSpeed" to playbackState.playbackSpeed,
                "bufferedPosition" to playbackState.bufferedPosition,
                "playbackState" to playbackState.playbackState, // 0 = idle, 1 = buffering, 2 = ready, 3 = ended
            ),
        )
        android.util.Log.d(
            "EventChannelHandler",
            "Dispatched playback state to Flutter: isPlaying=${playbackState.isPlaying}, sink=${eventSink != null}",
        )
    }

    /**
     * Sends error to Flutter.
     */
    fun sendError(
        error: String,
        details: String? = null,
    ) {
        eventSink?.error(error, details, null)
    }

    /**
     * Subscribes to a Flow and sends events to Flutter.
     */
    fun <T> subscribeToFlow(
        flow: Flow<T>,
        converter: (T) -> Map<String, Any?>,
    ) {
        // Cancel previous subscription
        flowJob?.cancel()

        flowJob =
            coroutineScope.launch(Dispatchers.IO) {
                flow
                    .catch { e ->
                        withContext(Dispatchers.Main) {
                            sendError("FLOW_ERROR", e.message)
                        }
                    }.collect { value ->
                        val event = converter(value)
                        withContext(Dispatchers.Main) {
                            eventSink?.success(event)
                        }
                    }
            }
    }

    /**
     * Sets the event sink (called by Flutter when listening).
     */
    fun setEventSink(sink: EventChannel.EventSink?) {
        android.util.Log.i("EventChannelHandler", "setEventSink called: sink=${sink != null}")
        this.eventSink = sink
    }

    /**
     * Clears the event sink (called when Flutter stops listening).
     */
    fun clearEventSink() {
        flowJob?.cancel()
        flowJob = null
        this.eventSink = null
    }
}
