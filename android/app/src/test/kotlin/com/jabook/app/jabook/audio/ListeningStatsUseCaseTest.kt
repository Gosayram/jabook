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

import com.jabook.app.jabook.audio.data.local.database.entity.ListeningDayStatEntity
import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.audio.domain.usecase.ListeningStatsUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListeningStatsUseCaseTest {
    private val repository: ListeningSessionRepository = mock()

    @Test
    fun `observeSummary aggregates totals from day stats`() =
        runTest {
            whenever(repository.observeDayStats(0L, 10L)).thenReturn(
                flowOf(
                    listOf(
                        ListeningDayStatEntity(
                            day = "2026-03-27",
                            playTimeMs = 3_000L,
                            contentTimeMs = 2_000L,
                            sessionsCount = 1,
                        ),
                        ListeningDayStatEntity(
                            day = "2026-03-28",
                            playTimeMs = 7_000L,
                            contentTimeMs = 5_000L,
                            sessionsCount = 2,
                        ),
                    ),
                ),
            )

            val useCase = ListeningStatsUseCase(repository)
            val summary = useCase.observeSummary(0L, 10L).single()

            assertEquals(10_000L, summary.totalPlayTimeMs)
            assertEquals(7_000L, summary.totalContentTimeMs)
            assertEquals(3, summary.totalSessions)
            assertEquals(2, summary.activeDays)
        }
}
