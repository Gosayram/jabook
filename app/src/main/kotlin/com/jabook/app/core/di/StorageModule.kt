package com.jabook.app.core.di

import com.jabook.app.core.storage.FileManager
import com.jabook.app.core.storage.FileManagerImpl
import com.jabook.app.shared.debug.IDebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for storage system dependencies */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
  /** Provides file manager for audiobook storage */
  @Provides
  @Singleton
  fun provideFileManager(
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    debugLogger: IDebugLogger,
  ): FileManager = FileManagerImpl(context, debugLogger)
}
