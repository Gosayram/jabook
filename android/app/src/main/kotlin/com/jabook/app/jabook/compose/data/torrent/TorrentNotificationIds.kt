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
 * Stable, collision-resistant notification IDs derived from a torrent's info hash.
 *
 * String.hashCode() collisions between different 40-char hex hashes would silently
 * overwrite each other's notifications and PendingIntents; CRC32 (masked positive)
 * is far more collision-resistant while staying deterministic across restarts.
 */
public object TorrentNotificationIds {
    public fun forHash(hash: String): Int =
        (
            java.util.zip
                .CRC32()
                .apply { update(hash.toByteArray()) }
                .value and 0x7FFFFFFF
        ).toInt()

    public fun forAction(
        hash: String?,
        action: String,
    ): Int {
        val base = hash?.let { forHash(it) } ?: 0
        return (base * 31 + action.hashCode()) and 0x7FFFFFFF
    }
}
