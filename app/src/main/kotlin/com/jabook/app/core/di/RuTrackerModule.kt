package com.jabook.app.core.di

import com.jabook.app.core.data.network.MockRuTrackerApiService
import com.jabook.app.core.data.network.RuTrackerApiService
import com.jabook.app.core.data.repository.RuTrackerRepositoryImpl
import com.jabook.app.core.domain.repository.RuTrackerRepository
import com.jabook.app.shared.debug.IDebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RuTrackerModule {

    @Provides
    @Singleton
    fun provideRuTrackerApiService(): RuTrackerApiService {
        return MockRuTrackerApiService()
    }

    @Provides
    @Singleton
    fun provideRuTrackerRepository(apiService: RuTrackerApiService, debugLogger: IDebugLogger): RuTrackerRepository {
        return RuTrackerRepositoryImpl(apiService, debugLogger)
    }

    // Real RuTracker API implementation will be added later
    // This module currently provides mock implementation for development
}
