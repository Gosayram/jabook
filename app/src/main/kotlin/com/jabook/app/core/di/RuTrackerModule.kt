package com.jabook.app.core.di

import com.jabook.app.core.data.repository.RuTrackerRepositoryImpl
import com.jabook.app.core.domain.repository.RuTrackerRepository
import com.jabook.app.core.network.RuTrackerApiClient
import com.jabook.app.core.network.RuTrackerApiClientImpl
import com.jabook.app.core.network.RuTrackerAvailabilityChecker
import com.jabook.app.core.network.RuTrackerParser
import com.jabook.app.core.network.RuTrackerParserImpl
import com.jabook.app.core.network.RuTrackerPreferences
import com.jabook.app.core.network.RuTrackerPreferencesImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for RuTracker dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RuTrackerModule {
    @Binds
    @Singleton
    abstract fun bindRuTrackerParser(impl: RuTrackerParserImpl): RuTrackerParser

    @Binds
    @Singleton
    abstract fun bindRuTrackerPreferences(impl: RuTrackerPreferencesImpl): RuTrackerPreferences

    @Binds
    @Singleton
    abstract fun bindRuTrackerApiClient(impl: RuTrackerApiClientImpl): RuTrackerApiClient

    @Binds
    @Singleton
    abstract fun bindRuTrackerRepository(
        impl: RuTrackerRepositoryImpl,
    ): RuTrackerRepository
}
