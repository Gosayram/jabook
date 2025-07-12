package com.jabook.app.core.di

import android.content.Context
import com.jabook.app.core.database.JaBookDatabase
import com.jabook.app.core.database.dao.AudiobookDao
import com.jabook.app.core.database.dao.BookmarkDao
import com.jabook.app.core.database.dao.ChapterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for providing database-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /** Provides the singleton Room database instance. */
    @Provides
    @Singleton
    fun provideJaBookDatabase(@ApplicationContext context: Context): JaBookDatabase {
        return JaBookDatabase.getInstance(context)
    }

    /** Provides the AudiobookDao from the database. */
    @Provides
    fun provideAudiobookDao(database: JaBookDatabase): AudiobookDao {
        return database.audiobookDao()
    }

    /** Provides the ChapterDao from the database. */
    @Provides
    fun provideChapterDao(database: JaBookDatabase): ChapterDao {
        return database.chapterDao()
    }

    /** Provides the BookmarkDao from the database. */
    @Provides
    fun provideBookmarkDao(database: JaBookDatabase): BookmarkDao {
        return database.bookmarkDao()
    }
}
