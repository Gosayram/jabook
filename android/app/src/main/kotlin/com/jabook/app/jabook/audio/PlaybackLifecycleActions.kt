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

/**
 * Encapsulates cross-cutting side effects triggered by play/pause/stop lifecycle transitions.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Keeps the service's lifecycle methods thin by moving orchestration here.
 *
 * Side effects managed:
 * - Phone call listener start/stop
 * - Listening session tracking (start/stop)
 * - Periodic position saving (start/save/stop)
 * - Crash context updates
 */
internal class PlaybackLifecycleActions(
    private val getPhoneCallListener: () -> PhoneCallListener?,
    private val getListeningSessionTracker: () -> ListeningSessionTracker,
    private val getPeriodicPositionSaver: () -> PeriodicPositionSaver,
    private val updateCrashContext: () -> Unit,
) {
    /** Called when playback starts — activates all "on play" side effects. */
    fun onPlay() {
        getPhoneCallListener()?.startListening()
        getListeningSessionTracker().onPlaybackStarted()
        getPeriodicPositionSaver().start()
        updateCrashContext()
    }

    /** Called when playback pauses — activates all "on pause" side effects. */
    fun onPause() {
        getListeningSessionTracker().onPlaybackStopped(reason = "pause")
        getPeriodicPositionSaver().save()
        getPeriodicPositionSaver().stop()
        updateCrashContext()
    }

    /** Called when playback stops — activates all "on stop" side effects. */
    fun onStop() {
        getPhoneCallListener()?.stopListening()
        getListeningSessionTracker().onPlaybackStopped(reason = "stop")
        getPeriodicPositionSaver().save()
        getPeriodicPositionSaver().stop()
        updateCrashContext()
    }
}
