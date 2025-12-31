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

package com.jabook.app.jabook.compose.data.torrent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room entity for persisting torrent downloads
 */
@Entity(tableName = "torrent_downloads")
@TypeConverters(TorrentDownloadConverters::class)
data class TorrentDownloadEntity(
    @PrimaryKey
    val hash: String,
    val name: String,
    val state: TorrentState,
    val progress: Float,
    val totalSize: Long,
    val downloadedSize: Long,
    val uploadedSize: Long,
    val savePath: String,
    val files: List<TorrentFile>,
    val errorMessage: String?,
    val addedTime: Long,
    val completedTime: Long,
    val pauseReason: PauseReason?,
    val topicId: String? = null,
) {
    /**
     * Convert to domain model
     */
    fun toDomain(): TorrentDownload =
        TorrentDownload(
            hash = hash,
            name = name,
            state = state,
            progress = progress,
            totalSize = totalSize,
            downloadedSize = downloadedSize,
            uploadedSize = uploadedSize,
            savePath = savePath,
            files = files,
            errorMessage = errorMessage,
            addedTime = addedTime,
            completedTime = completedTime,
            pauseReason = pauseReason,
            topicId = topicId,
        )

    companion object {
        /**
         * Create from domain model
         */
        fun fromDomain(download: TorrentDownload): TorrentDownloadEntity =
            TorrentDownloadEntity(
                hash = download.hash,
                name = download.name,
                state = download.state,
                progress = download.progress,
                totalSize = download.totalSize,
                downloadedSize = download.downloadedSize,
                uploadedSize = download.uploadedSize,
                savePath = download.savePath,
                files = download.files,
                errorMessage = download.errorMessage,
                addedTime = download.addedTime,
                completedTime = download.completedTime,
                pauseReason = download.pauseReason,
                topicId = download.topicId,
            )
    }
}

/**
 * Type converters for Room
 */
class TorrentDownloadConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromTorrentState(state: TorrentState): String = state.name

    @TypeConverter
    fun toTorrentState(value: String): TorrentState = TorrentState.valueOf(value)

    @TypeConverter
    fun fromPauseReason(reason: PauseReason?): String? = reason?.name

    @TypeConverter
    fun toPauseReason(value: String?): PauseReason? = value?.let { PauseReason.valueOf(it) }

    @TypeConverter
    fun fromFileList(files: List<TorrentFile>): String = gson.toJson(files)

    @TypeConverter
    fun toFileList(value: String): List<TorrentFile> {
        val type = object : TypeToken<List<TorrentFile>>() {}.type
        return gson.fromJson(value, type)
    }
}
