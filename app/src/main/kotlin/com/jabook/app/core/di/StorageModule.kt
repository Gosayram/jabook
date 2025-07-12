package com.jabook.app.core.di

import android.content.Context
import com.jabook.app.core.storage.StorageManager
import com.jabook.app.core.storage.StorageManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for storage dependencies */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager {
        return StorageManagerImpl(context)
    }
}
