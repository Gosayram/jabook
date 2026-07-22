package com.jabook.app.jabook.compose.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `lufs_value` column to chapters table for per-chapter loudness analysis.
 *
 * This allows [ChapterLoudnessTransitionPolicy] to normalize volume between
 * chapters of the same book when they have different recorded loudness levels.
 */
public val MIGRATION_25_26: Migration =
    object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing =
                db.query("PRAGMA table_info(chapters)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("lufs_value" !in existing) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN lufs_value REAL DEFAULT NULL")
            }
        }
    }
