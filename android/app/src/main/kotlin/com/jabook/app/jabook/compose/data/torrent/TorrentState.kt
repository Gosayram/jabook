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

/**
 * Torrent download state
 */
enum class TorrentState {
    /** Queued for download */
    QUEUED,

    /** Checking existing files */
    CHECKING,

    /** Downloading metadata from peers */
    DOWNLOADING_METADATA,

    /** Actively downloading */
    DOWNLOADING,

    /** Streaming playback in progress */
    STREAMING,

    /** Paused by user */
    PAUSED,

    /** Seeding (upload only) */
    SEEDING,

    /** Completed download */
    COMPLETED,

    /** Error occurred */
    ERROR,

    /** Stopped (not in session) */
    STOPPED,
}
