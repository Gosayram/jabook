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
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

/**
 * Tracks playback statistics like total listening time, skips, etc.
 *
 * P-18: CrossFadePlayer: статистика воспроизведения (Playback statistics)
 */
public class PlaybackStatisticsManager(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_stats", Context.MODE_PRIVATE),
) {
    private val statsKey = "playback_stats_v1"

    /**
     * Tracks total listening time in milliseconds.
     */
    public var totalListeningTimeMs: Long
        get() = prefs.getLong("total_listening_time_ms", 0)
        set(value) {
            prefs.edit { putLong("total_listening_time_ms", value) }
        }

    /**
     * Tracks number of tracks completed.
     */
    public var tracksCompleted: Int
        get() = prefs.getInt("tracks_completed", 0)
        set(value) {
            prefs.edit { putInt("tracks_completed", value) }
        }

    /**
     * Tracks number of skips.
     */
    public var skips: Int
        get() = prefs.getInt("skips", 0)
        set(value) {
            prefs.edit { putInt("skips", value) }
        }

    /**
     * Tracks last playback date.
     */
    public var lastPlaybackDate: Long
        get() = prefs.getLong("last_playback_date", 0)
        set(value) {
            prefs.edit { putLong("last_playback_date", value) }
        }

    /**
     * Adds listening time.
     */
    public fun addListeningTime(millis: Long) {
        totalListeningTimeMs += millis
        lastPlaybackDate = System.currentTimeMillis()
    }

    /**
     * Increments track completed count.
     */
    public fun incrementTracksCompleted() {
        tracksCompleted++
        lastPlaybackDate = System.currentTimeMillis()
    }

    /**
     * Increments skip count.
     */
    public fun incrementSkips() {
        skips++
        lastPlaybackDate = System.currentTimeMillis()
    }

    /**
     * Resets all statistics.
     */
    public fun reset() {
        totalListeningTimeMs = 0
        tracksCompleted = 0
        skips = 0
        lastPlaybackDate = 0
    }

    /**
     * Gets statistics as a map.
     */
    public fun getStatsMap(): Map<String, Any> =
        mapOf(
            "total_listening_time_ms" to totalListeningTimeMs,
            "tracks_completed" to tracksCompleted,
            "skips" to skips,
            "last_playback_date" to lastPlaybackDate,
            "total_listening_time_formatted" to formatTime(totalListeningTimeMs),
        )

    private fun formatTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
