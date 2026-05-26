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

import javax.inject.Inject
import kotlin.math.abs

/**
 * P-70: Analyzes listening patterns and suggests playback speed based on context.
 *
 * After collecting 10+ sessions, the analyzer can suggest a speed for a given
 * context (hour of day, day of week, output type) based on historical behavior.
 *
 * Usage:
 * ```
 * val suggestion = analyzer.suggestSpeed(
 *     ListeningContext(hourOfDay = 22, outputType = AudioOutputType.SPEAKER)
 * )
 * suggestion?.let { applySpeed(it) }
 * ```
 */
public class ListeningHabitAnalyzer
    @Inject
    constructor() {
        /**
         * Context for a listening session.
         */
        public data class ListeningContext(
            val hourOfDay: Int,
            val dayOfWeek: Int,
            val outputType: String,
        )

        /**
         * A completed listening session record.
         */
        public data class SessionRecord(
            val hourOfDay: Int,
            val dayOfWeek: Int,
            val outputType: String,
            val playbackSpeed: Float,
        )

        private val sessions = mutableListOf<SessionRecord>()

        /**
         * Records a completed session for future analysis.
         */
        public fun recordSession(record: SessionRecord) {
            sessions.add(record)
            if (sessions.size > MAX_SESSIONS) {
                sessions.removeAt(0)
            }
        }

        /**
         * Suggests a playback speed for the given context.
         *
         * @return Suggested speed, or null if not enough data (< 10 sessions)
         */
        public fun suggestSpeed(context: ListeningContext): Float? {
            if (sessions.size < MIN_SESSIONS_FOR_SUGGESTION) return null

            val similarSessions =
                sessions.filter { session ->
                    abs(session.hourOfDay - context.hourOfDay) <= HOUR_TOLERANCE &&
                        session.outputType == context.outputType
                }

            if (similarSessions.size < MIN_SIMILAR_SESSIONS) return null

            val avgSpeed = similarSessions.map { it.playbackSpeed }.average().toFloat()
            return avgSpeed.coerceIn(MIN_SPEED, MAX_SPEED)
        }

        /**
         * Returns the most common listening hour, or -1 if no data.
         */
        public fun getMostCommonListeningHour(): Int {
            if (sessions.isEmpty()) return -1
            return sessions
                .groupBy { it.hourOfDay }
                .maxByOrNull { it.value.size }
                ?.key ?: -1
        }

        /**
         * Returns the number of recorded sessions.
         */
        public fun getSessionCount(): Int = sessions.size

        /**
         * Clears all recorded sessions.
         */
        public fun clear() {
            sessions.clear()
        }

        public companion object {
            private const val TAG = "ListeningHabitAnalyzer"
            private const val MIN_SESSIONS_FOR_SUGGESTION = 10
            private const val MIN_SIMILAR_SESSIONS = 3
            private const val HOUR_TOLERANCE = 2
            private const val MAX_SESSIONS = 500
            internal const val MIN_SPEED = 0.5f
            internal const val MAX_SPEED = 4.0f
        }
    }
