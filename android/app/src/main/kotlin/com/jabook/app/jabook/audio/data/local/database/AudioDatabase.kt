// Copyright 2025 Jabook Contributors
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
import com.jabook.app.jabook.audio.data.local.dao.PlaybackPositionDao
import com.jabook.app.jabook.audio.data.local.dao.PlaylistDao
import com.jabook.app.jabook.audio.data.local.dao.SavedPlayerStateDao
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity
import com.jabook.app.jabook.audio.data.local.database.entity.PlaybackPositionEntity
import com.jabook.app.jabook.audio.data.local.database.entity.PlaylistEntity
import com.jabook.app.jabook.audio.data.local.database.entity.SavedPlayerStateEntity

/**
 * Room database for audio player data.
 *
 * Version 2 adds SavedPlayerStateEntity for full player state persistence.
 */
@Database(
    entities = [
        PlaybackPositionEntity::class,
        PlaylistEntity::class,
        ChapterMetadataEntity::class,
        SavedPlayerStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AudioDatabase : RoomDatabase() {
    abstract fun playbackPositionDao(): PlaybackPositionDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun chapterMetadataDao(): ChapterMetadataDao

    abstract fun savedPlayerStateDao(): SavedPlayerStateDao
}
