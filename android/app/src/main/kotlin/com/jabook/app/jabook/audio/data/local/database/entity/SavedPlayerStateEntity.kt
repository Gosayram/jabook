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

package com.jabook.app.jabook.audio.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a saved full player state.
 *
 * This includes playlist, position, speed, repeat mode, and sleep timer.
 */
@Entity(tableName = "saved_player_states")
data class SavedPlayerStateEntity(
    @PrimaryKey
    val groupPath: String,
    val filePaths: String, // JSON array of file paths
    val metadata: String? = null, // JSON object of metadata (title, artist, album, coverPath)
    val currentIndex: Int = 0,
    val currentPosition: Long = 0,
    val playbackSpeed: Double = 1.0,
    val isPlaying: Boolean = false,
    val repeatMode: Int = 0, // 0 = none, 1 = track, 2 = playlist
    val sleepTimerRemainingSeconds: Int? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)
