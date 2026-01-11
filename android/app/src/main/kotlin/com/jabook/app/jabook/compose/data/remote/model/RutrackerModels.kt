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

package com.jabook.app.jabook.compose.data.remote.model

/**
 * Search result item from Rutracker.
 *
 * Represents a single audiobook found in search results.
 */
public data class SearchResult(
    /** Unique topic ID */
    public val topicId: String,
    /** Book title */
    public val title: String,
    /** Author/narrator */
    public val author: String,
    /** Category/genre */
    public val category: String,
    /** File size (formatted, e.g., "1.5 GB") */
    public val size: String,
    /** Number of seeders */
    public val seeders: Int,
    /** Number of leechers */
    public val leechers: Int,
    /** Magnet link for direct download */
    public val magnetUrl: String?,
    /** Torrent download URL */
    public val torrentUrl: String,
    /** Cover image URL (optional, may not be available in search results) */
    public val coverUrl: String? = null,
    /** Uploader nickname (optional) */
    public val uploader: String? = null,
)

/**
 * Detailed information about a topic/audiobook.
 */
public data class TopicDetails(
    /** Topic ID */
    public val topicId: String,
    /** Full title */
    public val title: String,
    /** Author or narrator info */
    public val author: String?,
    /** Book performer/reader */
    public val performer: String?,
    /** Category */
    public val category: String,
    /** File size */
    public val size: String,
    /** Seeders count */
    public val seeders: Int,
    /** Leechers count */
    public val leechers: Int,
    /** Magnet link */
    public val magnetUrl: String?,
    /** Torrent file URL */
    public val torrentUrl: String,
    /** Cover image URL */
    public val coverUrl: String?,
    /** List of genres */
    public val genres: List<String>,
    /** Date added */
    public val addedDate: String?,
    /** Duration (e.g., "08:05:13") */
    public val duration: String?,
    /** Bitrate (e.g., "128 kbps") */
    public val bitrate: String?,
    /** Audio codec (e.g., "MP3") */
    public val audioCodec: String?,
    /** Description/content (plain text) */
    public val description: String?,
    /** Description HTML (preserves links and formatting) */
    public val descriptionHtml: String? = null,
    /** Structured MediaInfo data */
    public val mediaInfo: MediaInfo? = null,
    /** Related audiobooks from same series */
    public val relatedBooks: List<RelatedBook>,
    /** Series/cycle name */
    public val series: String? = null,
    /** Comments from topic */
    public val comments: List<Comment> = emptyList(),
    /** Registration date */
    public val registeredDate: String? = null,
    /** Download count */
    public val downloadsCount: String? = null,
    /** Current page number (1-indexed) */
    public val currentPage: Int = 1,
    /** Total number of pages */
    public val totalPages: Int = 1,
    /** All extracted metadata fields */
    public val allMetadata: Map<String, String> = emptyMap(),
)

/**
 * Comment from topic page.
 */
public data class Comment(
    /** Comment ID */
    public val id: String,
    /** Author username */
    public val author: String,
    /** Comment date/time */
    public val date: String,
    /** Comment text (plain text, links removed) */
    public val text: String,
    /** Comment HTML (preserves links and formatting) */
    public val html: String? = null,
    /** Avatar image URL (optional) */
    public val avatarUrl: String? = null,
)

/**
 * Related audiobook from the same series/cycle.
 */
public data class RelatedBook(
    /** Topic ID */
    public val topicId: String,
    /** Book title */
    public val title: String,
)

/**
 * Torrent metadata.
 */
public data class TorrentInfo(
    /** Info hash */
    public val infoHash: String,
    /** Torrent name */
    public val name: String,
    /** Total size in bytes */
    public val totalSize: Long,
    /** List of files in torrent */
    public val files: List<TorrentFile>,
)

/**
 * File within a torrent.
 */
public data class TorrentFile(
    /** File path within torrent */
    public val path: String,
    /** File size in bytes */
    public val size: Long,
)
