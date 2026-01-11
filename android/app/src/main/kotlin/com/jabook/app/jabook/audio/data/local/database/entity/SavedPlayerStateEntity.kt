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

package com.jabook.app.jabook.audio.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a saved full player state.
 *
 * This includes playlist, position, speed, repeat mode, and sleep timer.
 */
@Entity(tableName = "saved_player_states")
public data class SavedPlayerStateEntity(
    @PrimaryKey
    public val groupPath: String,
    public val filePaths: String, // JSON array of file paths
    public val metadata: String? = null, // JSON object of metadata (title, artist, album, coverPath)
    public val currentIndex: Int = 0,
    public val currentPosition: Int = ,
    public val playbackSpeed: Double = 1.0,
    public val isPlaying: Boolean = false,
    public val repeatMode: Int = 0, // 0 = none, 1 = track, 2 = playlist
    public val sleepTimerRemainingSeconds: Int? = null,
    public val lastUpdated: Long = System.currentTimeMillis(),
)
