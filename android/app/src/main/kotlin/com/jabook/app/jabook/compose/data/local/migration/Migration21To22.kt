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
 * Adds `resumeData` BLOB column to `torrent_downloads`.
 *
 * libtorrent4j resume data is serialized to bytes via `SaveResumeDataAlert`
 * and stored here so that in-progress downloads survive process death.
 * The column defaults to NULL — existing rows without resume data will be
 * re-added from the magnet URI on the next session start instead.
 */
public val MIGRATION_21_22: Migration =
    object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE torrent_downloads ADD COLUMN resumeData BLOB")
        }
    }
