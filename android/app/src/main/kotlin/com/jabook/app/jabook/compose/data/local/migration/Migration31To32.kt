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
 * Adds indices on download_history (status, completedAt) and download_queue (status) for faster queries.
 */
public val MIGRATION_31_32: Migration =
    object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_history_status` ON `download_history` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_history_completedAt` ON `download_history` (`completedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_queue_status` ON `download_queue` (`status`)")
        }
    }
