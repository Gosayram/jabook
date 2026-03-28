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

package com.jabook.app.jabook.audio.domain.usecase

import com.jabook.app.jabook.audio.data.local.database.entity.ListeningDayStatEntity
import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

public data class ListeningStatsSummary(
    val totalPlayTimeMs: Long,
    val totalContentTimeMs: Long,
    val totalSessions: Int,
    val activeDays: Int,
)

public class ListeningStatsUseCase
    @Inject
    constructor(
        private val listeningSessionRepository: ListeningSessionRepository,
    ) {
        public fun observeDayStats(
            fromEpochMs: Long,
            toEpochMs: Long,
        ): Flow<List<ListeningDayStatEntity>> =
            listeningSessionRepository.observeDayStats(
                fromEpochMs = fromEpochMs,
                toEpochMs = toEpochMs,
            )

        public fun observeSummary(
            fromEpochMs: Long,
            toEpochMs: Long,
        ): Flow<ListeningStatsSummary> =
            observeDayStats(
                fromEpochMs = fromEpochMs,
                toEpochMs = toEpochMs,
            ).map { dayStats ->
                ListeningStatsSummary(
                    totalPlayTimeMs = dayStats.sumOf { it.playTimeMs },
                    totalContentTimeMs = dayStats.sumOf { it.contentTimeMs },
                    totalSessions = dayStats.sumOf { it.sessionsCount },
                    activeDays = dayStats.size,
                )
            }
    }
