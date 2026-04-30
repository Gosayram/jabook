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
 * Facade for sleep timer operations, reducing delegation boilerplate in AudioPlayerService.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates all sleep timer queries and commands in one place.
 */
internal class SleepTimerFacade(
    private val getSleepTimerManager: () -> SleepTimerManager?,
    private val getActivePlayer: () -> androidx.media3.exoplayer.ExoPlayer,
    private val updateCrashContext: () -> Unit = {},
) {
    fun setSleepTimerMinutes(minutes: Int) {
        getSleepTimerManager()?.setSleepTimerMinutes(minutes)
        updateCrashContext()
    }

    fun setSleepTimerEndOfChapter() {
        getSleepTimerManager()?.setSleepTimerEndOfChapter()
        updateCrashContext()
    }

    fun setSleepTimerEndOfChapterOrFallback(): Boolean =
        getSleepTimerManager()?.setSleepTimerEndOfChapterOrFallback(
            getActivePlayer().mediaItemCount > 1,
        ) ?: false

    fun setSleepTimerEndOfTrack() {
        getSleepTimerManager()?.setSleepTimerEndOfTrack()
        updateCrashContext()
    }

    fun cancelSleepTimer() {
        getSleepTimerManager()?.cancelSleepTimer()
        updateCrashContext()
    }

    fun getSleepTimerRemainingSeconds(): Int? = getSleepTimerManager()?.getSleepTimerRemainingSeconds()

    fun isSleepTimerActive(): Boolean = getSleepTimerManager()?.isSleepTimerActive() ?: false

    fun isSleepTimerEndOfChapter(): Boolean = getSleepTimerManager()?.sleepTimerEndOfChapter == true

    fun isSleepTimerEndOfTrack(): Boolean = getSleepTimerManager()?.sleepTimerEndOfTrack == true
}
