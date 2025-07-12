package com.jabook.app.core.di

import com.jabook.app.core.data.network.MockRuTrackerApiService
import com.jabook.app.core.data.network.RuTrackerApiService
import com.jabook.app.core.data.repository.RuTrackerRepositoryImpl
import com.jabook.app.core.domain.repository.RuTrackerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for RuTracker integration dependencies Provides API service and repository implementations */
@Module
@InstallIn(SingletonComponent::class)
object RuTrackerModule {

    /**
     * Provides RuTracker API service For now, returns mock implementation
     *
     * FIXME: Replace with actual Retrofit implementation
     */
    @Provides
    @Singleton
    fun provideRuTrackerApiService(): RuTrackerApiService {
        return MockRuTrackerApiService()
    }

    /** Provides RuTracker repository */
    @Provides
    @Singleton
    fun provideRuTrackerRepository(debugLogger: com.jabook.app.shared.debug.IDebugLogger): RuTrackerRepository {
        return RuTrackerRepositoryImpl(debugLogger)
    }
}
