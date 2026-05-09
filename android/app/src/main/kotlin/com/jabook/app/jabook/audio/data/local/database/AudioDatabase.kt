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

package com.jabook.app.jabook.audio.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jabook.app.jabook.audio.data.local.dao.ChapterMetadataDao
import com.jabook.app.jabook.audio.data.local.dao.ListeningSessionDao
import com.jabook.app.jabook.audio.data.local.dao.PlaybackPositionDao
import com.jabook.app.jabook.audio.data.local.dao.PlaylistDao
import com.jabook.app.jabook.audio.data.local.dao.SavedPlayerStateDao
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity
import com.jabook.app.jabook.audio.data.local.database.entity.ListeningSessionEntity
import com.jabook.app.jabook.audio.data.local.database.entity.PlaybackPositionEntity
import com.jabook.app.jabook.audio.data.local.database.entity.PlaylistEntity
import com.jabook.app.jabook.audio.data.local.database.entity.SavedPlayerStateEntity

/**
 * Room database for audio player data.
 *
 * Schema evolution:
 * - Version 2 added [SavedPlayerStateEntity] for full player state persistence.
 * - Version 3 adds [ListeningSessionEntity] and related indexes for engagement analytics.
 */
@Database(
    entities = [
        PlaybackPositionEntity::class,
        PlaylistEntity::class,
        ChapterMetadataEntity::class,
        SavedPlayerStateEntity::class,
        ListeningSessionEntity::class,
    ],
    version = AudioDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
public abstract class AudioDatabase : RoomDatabase() {
    public companion object {
        public const val SCHEMA_VERSION: Int = 3
    }

    public abstract fun playbackPositionDao(): PlaybackPositionDao

    public abstract fun playlistDao(): PlaylistDao

    public abstract fun chapterMetadataDao(): ChapterMetadataDao

    public abstract fun savedPlayerStateDao(): SavedPlayerStateDao

    public abstract fun listeningSessionDao(): ListeningSessionDao
}
