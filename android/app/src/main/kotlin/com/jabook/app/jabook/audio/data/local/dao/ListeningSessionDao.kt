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

package com.jabook.app.jabook.audio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.audio.data.local.database.entity.ListeningDayStatEntity
import com.jabook.app.jabook.audio.data.local.database.entity.ListeningSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface ListeningSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun upsert(session: ListeningSessionEntity)

    @Query("SELECT * FROM listening_sessions WHERE id = :sessionId")
    public suspend fun getById(sessionId: String): ListeningSessionEntity?

    @Query("SELECT * FROM listening_sessions WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    public suspend fun getLatestActiveSession(): ListeningSessionEntity?

    @Query(
        """
        UPDATE listening_sessions
        SET ended_at = :endedAt,
            position_end_ms = :positionEndMs,
            speed_factor = :speedFactor,
            chapter_index = :chapterIndex,
            updated_at = :updatedAt
        WHERE id = :sessionId
        """,
    )
    public suspend fun finishSession(
        sessionId: String,
        endedAt: Long,
        positionEndMs: Long,
        speedFactor: Float,
        chapterIndex: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', started_at / 1000, 'unixepoch', 'localtime') AS day,
            COALESCE(SUM(CASE WHEN ended_at IS NULL THEN 0 ELSE ABS(position_end_ms - position_start_ms) END), 0) AS playTimeMs,
            COALESCE(SUM(CASE
                WHEN ended_at IS NULL THEN 0
                WHEN speed_factor <= 0 THEN ABS(position_end_ms - position_start_ms)
                ELSE CAST(ABS(position_end_ms - position_start_ms) / speed_factor AS INTEGER)
            END), 0) AS contentTimeMs,
            COUNT(*) AS sessionsCount
        FROM listening_sessions
        WHERE started_at >= :fromEpochMs AND started_at <= :toEpochMs
        GROUP BY day
        ORDER BY day ASC
        """,
    )
    public fun observeDayStats(
        fromEpochMs: Long,
        toEpochMs: Long,
    ): Flow<List<ListeningDayStatEntity>>
}
