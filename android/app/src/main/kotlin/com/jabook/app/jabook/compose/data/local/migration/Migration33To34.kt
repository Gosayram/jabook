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

package com.jabook.app.jabook.compose.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Moves the libtorrent resume-data BLOB out of `torrent_downloads` into its own
 * `torrent_resume` one-to-one table. List reads (TorrentDownloadRow) then never
 * materialize multi-KB resume BLOBs for every row.
 *
 * The column drop is done via a table rebuild (CREATE-new → copy → drop → rename)
 * because `ALTER TABLE DROP COLUMN` requires SQLite >= 3.35 (Android 13+); the app
 * supports API 30-32 where the framework SQLite predates it.
 */
public val MIGRATION_33_34: Migration =
    object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. New one-to-one resume table.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `torrent_resume` (
                    `hash` TEXT NOT NULL,
                    `resumeData` BLOB NOT NULL,
                    PRIMARY KEY(`hash`)
                )
                """.trimIndent(),
            )

            // 2. Copy resume BLOBs BEFORE dropping the column.
            db.execSQL(
                """
                INSERT INTO `torrent_resume` (`hash`, `resumeData`)
                SELECT `hash`, `resumeData` FROM `torrent_downloads`
                WHERE `resumeData` IS NOT NULL
                """.trimIndent(),
            )

            // 3. Rebuild torrent_downloads without resumeData.
            db.execSQL(
                """
                CREATE TABLE `torrent_downloads_new` (
                    `hash` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `progress` REAL NOT NULL,
                    `totalSize` INTEGER NOT NULL,
                    `downloadedSize` INTEGER NOT NULL,
                    `uploadedSize` INTEGER NOT NULL,
                    `savePath` TEXT NOT NULL,
                    `files` TEXT NOT NULL,
                    `errorMessage` TEXT,
                    `addedTime` INTEGER NOT NULL,
                    `completedTime` INTEGER NOT NULL,
                    `pauseReason` TEXT,
                    `topicId` TEXT,
                    PRIMARY KEY(`hash`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `torrent_downloads_new` (
                    `hash`, `name`, `state`, `progress`, `totalSize`, `downloadedSize`,
                    `uploadedSize`, `savePath`, `files`, `errorMessage`, `addedTime`,
                    `completedTime`, `pauseReason`, `topicId`
                )
                SELECT `hash`, `name`, `state`, `progress`, `totalSize`, `downloadedSize`,
                       `uploadedSize`, `savePath`, `files`, `errorMessage`, `addedTime`,
                       `completedTime`, `pauseReason`, `topicId`
                FROM `torrent_downloads`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `torrent_downloads`")
            db.execSQL("ALTER TABLE `torrent_downloads_new` RENAME TO `torrent_downloads`")
        }
    }
