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
 * Migration from database version 37 to 38.
 *
 * Adds the nullable `topic_date` column to `cached_topics` — the topic's
 * release/registration date (epoch millis) parsed from the tracker listing
 * during indexing. Readers order "recent" queries by
 * `COALESCE(topic_date, timestamp)` so legacy rows keep today's behavior.
 *
 * Deliberately NO backfill: setting `topic_date = timestamp` would re-mark
 * every old topic as freshly released. Legacy rows stay NULL until re-indexed.
 */
public val MIGRATION_37_38: Migration =
    Migration(37, 38) { db: SupportSQLiteDatabase ->
        db.execSQL("ALTER TABLE `cached_topics` ADD COLUMN `topic_date` INTEGER")
    }
