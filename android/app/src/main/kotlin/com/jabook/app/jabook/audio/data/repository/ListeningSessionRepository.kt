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

package com.jabook.app.jabook.audio.data.repository

import com.jabook.app.jabook.audio.data.local.dao.ListeningSessionDao
import com.jabook.app.jabook.audio.data.local.database.entity.ListeningDayStatEntity
import com.jabook.app.jabook.audio.data.local.database.entity.ListeningSessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class ListeningSessionRepository
    @Inject
    constructor(
        private val listeningSessionDao: ListeningSessionDao,
    ) {
        public suspend fun startSession(
            bookId: String,
            positionStartMs: Long,
            speedFactor: Float,
            chapterIndex: Int,
            startedAt: Long = System.currentTimeMillis(),
        ): String {
            val sessionId = UUID.randomUUID().toString()
            listeningSessionDao.upsert(
                ListeningSessionEntity(
                    id = sessionId,
                    bookId = bookId,
                    startedAt = startedAt,
                    positionStartMs = positionStartMs,
                    speedFactor = speedFactor,
                    chapterIndex = chapterIndex,
                    updatedAt = startedAt,
                ),
            )
            return sessionId
        }

        public suspend fun finishSession(
            sessionId: String,
            positionEndMs: Long,
            speedFactor: Float,
            chapterIndex: Int,
            endedAt: Long = System.currentTimeMillis(),
        ) {
            listeningSessionDao.finishSession(
                sessionId = sessionId,
                endedAt = endedAt,
                positionEndMs = positionEndMs,
                speedFactor = speedFactor,
                chapterIndex = chapterIndex,
                updatedAt = endedAt,
            )
        }

        public suspend fun getLatestActiveSession(): ListeningSessionEntity? = listeningSessionDao.getLatestActiveSession()

        public fun observeDayStats(
            fromEpochMs: Long,
            toEpochMs: Long,
        ): Flow<List<ListeningDayStatEntity>> = listeningSessionDao.observeDayStats(fromEpochMs, toEpochMs)
    }
