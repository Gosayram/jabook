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

package com.jabook.app.jabook.compose.data.model

/**
 * Domain model representing an audiobook.
 *
 * This is the domain representation used in UI layer.
 * It is converted from/to Room entities in the data layer.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val description: String?,
    val totalDuration: Long, // Total duration in milliseconds
    val addedDate: Long, // Timestamp when book was added
    val lastPlayedDate: Long?, // Timestamp of last playback, null if never played
    val currentPosition: Long, // Current playback position in milliseconds
    val currentChapterIndex: Int, // Index of current chapter
    val isDownloaded: Boolean,
    val downloadProgress: Float, // 0.0 to 1.0
    val chapters: List<Chapter>,
)

/**
 * Domain model representing a chapter in an audiobook.
 */
data class Chapter(
    val id: String,
    val bookId: String,
    val title: String,
    val chapterIndex: Int, // 0-based index
    val fileIndex: Int, // Index in torrent file
    val duration: Long, // Duration in milliseconds
    val fileUrl: String?, // Local file path if downloaded, null otherwise
    val isDownloaded: Boolean,
)
