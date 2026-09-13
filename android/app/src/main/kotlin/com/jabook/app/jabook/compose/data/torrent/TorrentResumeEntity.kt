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

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * libtorrent resume data BLOB for crash-safe download resumption, stored in its
 * own one-to-one table so list reads of [TorrentDownloadRow] never materialize
 * multi-KB resume BLOBs for every row.
 */
@Keep
@Entity(tableName = "torrent_resume")
public class TorrentResumeEntity(
    @PrimaryKey
    public val hash: String,
    public val resumeData: ByteArray,
)
