package com.jabook.app.jabook.compose.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `eq_preset_override` column to books table for per-book EQ preset override.
 *
 * This allows users to override the global EQ preset for individual books
 * that are poorly recorded and need a different equalization profile.
 */
public val MIGRATION_26_27: Migration =
    object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing =
                db.query("PRAGMA table_info(books)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("eq_preset_override" !in existing) {
                db.execSQL("ALTER TABLE books ADD COLUMN eq_preset_override TEXT DEFAULT NULL")
            }
        }
    }
