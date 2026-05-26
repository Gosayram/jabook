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

import com.jabook.app.jabook.util.LogUtils
import java.util.Calendar
import javax.inject.Inject

/**
 * P-72: Tracks daily listening goal progress and streak data.
 *
 * Users set a daily goal (e.g., 30 minutes). This class tracks
 * cumulative listening time per day, maintains streaks, and provides
 * motivation data for UI display.
 *
 * @param goalMinutes Daily listening goal in minutes
 * @param getTodayMinutes Provider for current day's listening minutes
 */
public class DailyGoalTracker
    @Inject
    constructor() {
        private var goalMinutes: Int = DEFAULT_GOAL_MINUTES
        private var consecutiveDays: Int = 0
        private var lastGoalMetDate: String = ""

        /**
         * Streak data for display.
         */
        public data class StreakData(
            val todayMinutes: Int,
            val goalMinutes: Int,
            val consecutiveDays: Int,
            val isGoalMet: Boolean,
        ) {
            /** Progress fraction 0.0–1.0 (may exceed 1.0). */
            val progress: Float
                get() = if (goalMinutes > 0) todayMinutes.toFloat() / goalMinutes else 0f
        }

        /**
         * Sets the daily listening goal.
         */
        public fun setGoal(minutes: Int) {
            require(minutes > 0) { "Goal must be positive, got $minutes" }
            goalMinutes = minutes
            LogUtils.d(TAG, "Daily goal set to $minutes minutes")
        }

        /**
         * Returns the current daily goal in minutes.
         */
        public fun getGoalMinutes(): Int = goalMinutes

        /**
         * Reports current day's listening minutes and returns streak data.
         *
         * Call this periodically or when checking goal status.
         *
         * @param todayMinutes Total minutes listened today
         * @return Current streak data
         */
        public fun reportProgress(todayMinutes: Int): StreakData {
            val today = todayDateString()
            val isMet = todayMinutes >= goalMinutes

            if (isMet && lastGoalMetDate != today) {
                consecutiveDays++
                lastGoalMetDate = today
                LogUtils.d(TAG, "Goal met! Streak: $consecutiveDays days")
            }

            return StreakData(
                todayMinutes = todayMinutes,
                goalMinutes = goalMinutes,
                consecutiveDays = consecutiveDays,
                isGoalMet = isMet,
            )
        }

        /**
         * Resets the streak counter.
         */
        public fun resetStreak() {
            consecutiveDays = 0
            lastGoalMetDate = ""
            LogUtils.d(TAG, "Streak reset")
        }

        /**
         * Returns the most common listening hour from recent data.
         * Delegates to [ListeningHabitAnalyzer] if available.
         */
        public fun getPreferredListeningHour(analyzer: ListeningHabitAnalyzer): Int = analyzer.getMostCommonListeningHour()

        private fun todayDateString(): String {
            val cal = Calendar.getInstance()
            return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }

        public companion object {
            private const val TAG = "DailyGoalTracker"
            internal const val DEFAULT_GOAL_MINUTES = 30
        }
    }
