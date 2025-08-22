package com.jabook.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jabook.app.core.database.converters.DatabaseConverters
import com.jabook.app.core.database.dao.AudiobookDao
import com.jabook.app.core.database.dao.BookmarkDao
import com.jabook.app.core.database.dao.ChapterDao
import com.jabook.app.core.database.entities.AudiobookEntity
import com.jabook.app.core.database.entities.BookmarkEntity
import com.jabook.app.core.database.entities.ChapterEntity

/** Main Room database for JaBook application. Contains all audiobook-related data including metadata, chapters, and bookmarks. */
@Database(entities = [AudiobookEntity::class, ChapterEntity::class, BookmarkEntity::class], version = 1, exportSchema = false)
@TypeConverters(DatabaseConverters::class)
abstract class JaBookDatabase : RoomDatabase() {
  /** Provides access to audiobook operations. */
  abstract fun audiobookDao(): AudiobookDao

  /** Provides access to chapter operations. */
  abstract fun chapterDao(): ChapterDao

  /** Provides access to bookmark operations. */
  abstract fun bookmarkDao(): BookmarkDao

  companion object {
    private const val DATABASE_NAME = "jabook_database"

    @Volatile private var INSTANCE: JaBookDatabase? = null

    /** Get the singleton database instance. */
    fun getInstance(context: Context): JaBookDatabase =
      INSTANCE
        ?: synchronized(this) {
          val instance =
            Room
              .databaseBuilder(context.applicationContext, JaBookDatabase::class.java, DATABASE_NAME)
              .fallbackToDestructiveMigration()
              .build()

          INSTANCE = instance
          instance
        }

    /** Clear the database instance for testing purposes. */
    internal fun clearInstance() {
      INSTANCE = null
    }
  }
}
