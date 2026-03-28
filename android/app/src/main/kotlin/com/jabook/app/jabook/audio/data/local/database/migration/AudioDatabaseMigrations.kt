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

package com.jabook.app.jabook.audio.data.local.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

public object AudioDatabaseMigrations {
    public val MIGRATION_2_3: Migration =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS listening_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        book_id TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER,
                        position_start_ms INTEGER NOT NULL,
                        position_end_ms INTEGER,
                        speed_factor REAL NOT NULL,
                        chapter_index INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_listening_sessions_book_id ON listening_sessions(book_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_listening_sessions_started_at ON listening_sessions(started_at)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_listening_sessions_ended_at ON listening_sessions(ended_at)",
                )
            }
        }
}
