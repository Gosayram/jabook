// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.jabook.app.jabook.compose.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuilds the RuTracker FTS index as contentless FTS5 to reduce database size. */
public val MIGRATION_24_25: Migration =
    object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS topics_fts_ai")
            db.execSQL("DROP TRIGGER IF EXISTS topics_fts_ad")
            db.execSQL("DROP TRIGGER IF EXISTS topics_fts_au")
            db.execSQL("DROP TABLE IF EXISTS topics_fts")
            createTopicsFts5Index(db)
        }
    }
