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
 * Represents the download status of a book or chapter.
 *
 * This enum is used to track the state of content downloads
 * and is stored as a string in the database.
 */
enum class DownloadStatus {
    /**
     * Content is not downloaded and must be streamed.
     */
    NOT_DOWNLOADED,

    /**
     * Content is currently being downloaded.
     * Check downloadProgress for completion percentage.
     */
    DOWNLOADING,

    /**
     * Content has been fully downloaded and is available offline.
     */
    DOWNLOADED,

    /**
     * Download failed due to an error.
     * User may retry download.
     */
    FAILED,

    ;

    companion object {
        /**
         * Safely convert from database string to enum.
         * Returns NOT_DOWNLOADED if string is invalid.
         */
        fun fromString(value: String?): DownloadStatus =
            runCatching {
                valueOf(value ?: "NOT_DOWNLOADED")
            }.getOrDefault(NOT_DOWNLOADED)
    }
}
