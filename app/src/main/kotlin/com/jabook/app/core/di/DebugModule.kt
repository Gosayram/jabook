package com.jabook.app.core.di

import android.content.Context
import com.jabook.app.shared.debug.DebugLoggerImpl
import com.jabook.app.shared.debug.IDebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for debug-related dependencies Provides DebugLogger instance */
@Module
@InstallIn(SingletonComponent::class)
class DebugModule {
    /** Provides DebugLogger instance Initializes it with application context */
    @Provides
    @Singleton
    fun provideDebugLogger(
        @ApplicationContext context: Context,
    ): IDebugLogger = DebugLoggerImpl(context)
}
