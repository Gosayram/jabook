package com.jabook.app.core.di

import android.content.Context
import com.jabook.app.core.cache.CacheConfig
import com.jabook.app.core.cache.RuTrackerCacheManager
import com.jabook.app.core.storage.FileManager
import com.jabook.app.core.storage.FileManagerImpl
import com.jabook.app.shared.debug.IDebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Hilt module for storage system dependencies */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
  /** Provides file manager for audiobook storage */
  @Provides
  @Singleton
  fun provideFileManager(
    @ApplicationContext context: Context,
    debugLogger: IDebugLogger,
  ): FileManager = FileManagerImpl(context, debugLogger)

  /** Provides cache directory for the application */
  @Provides
  @Singleton
  fun provideCacheDir(@ApplicationContext context: Context): File {
    return context.cacheDir
  }

  /** Provides cache configuration */
  @Provides
  @Singleton
  fun provideCacheConfig(): CacheConfig = CacheConfig()

  /** Provides cache manager */
  @Provides
  @Singleton
  fun provideCacheManager(
    debugLogger: IDebugLogger,
    cacheDir: File,
    config: CacheConfig,
  ): RuTrackerCacheManager = RuTrackerCacheManager(debugLogger, cacheDir, config)
}
