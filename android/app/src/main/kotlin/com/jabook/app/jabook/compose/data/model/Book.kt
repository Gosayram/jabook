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

package com.jabook.app.jabook.compose.data.model

/**
 * Domain model representing an audiobook.
 *
 * This is the domain representation used in UI layer.
 * It is converted from/to Room entities in the data layer.
 */
public data class Book(
    public val id: String,
    public val title: String,
    public val author: String,
    public val coverUrl: String?,
    public val description: String?,
    public val totalDuration: Long, // Total duration in milliseconds
    public val addedDate: Long, // Timestamp when book was added
    public val lastPlayedDate: Long?, // Timestamp of last playback, null if never played
    public val currentPosition: Long, // Current playback position in milliseconds
    public val currentChapterIndex: Int, // Index of current chapter
    public val isDownloaded: Boolean,
    public val downloadProgress: Float, // 0.0 to 1.0
    public val chapters: List<Chapter>,
)

/**
 * Domain model representing a chapter in an audiobook.
 */
public data class Chapter(
    public val id: String,
    public val bookId: String,
    public val title: String,
    public val chapterIndex: Int, // 0-based index
    public val fileIndex: Int, // Index in torrent file
    public val duration: Long, // Duration in milliseconds
    public val fileUrl: String?, // Local file path if downloaded, null otherwise
    public val isDownloaded: Boolean,
)
