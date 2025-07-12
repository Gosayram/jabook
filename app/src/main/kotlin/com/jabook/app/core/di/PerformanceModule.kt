package com.jabook.app.core.di

import com.jabook.app.shared.performance.PerformanceProfiler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for performance monitoring dependencies */
@Module
@InstallIn(SingletonComponent::class)
object PerformanceModule {

    @Provides
    @Singleton
    fun providePerformanceProfiler(debugLogger: com.jabook.app.shared.debug.IDebugLogger): PerformanceProfiler =
        PerformanceProfiler(debugLogger)
}
