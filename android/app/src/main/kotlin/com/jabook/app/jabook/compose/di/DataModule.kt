// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.DataStoreUserPreferencesRepository
import com.jabook.app.jabook.compose.data.repository.OfflineFirstBooksRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jabook_preferences")

/**
 * Hilt module for data layer dependencies.
 *
 * Provides Repository implementations to the rest of the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    /**
     * Binds the OfflineFirstBooksRepository implementation to the BooksRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindBooksRepository(repository: OfflineFirstBooksRepository): BooksRepository

    /**
     * Binds the DataStoreUserPreferencesRepository implementation to the UserPreferencesRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(repository: DataStoreUserPreferencesRepository): UserPreferencesRepository

    /**
     * Binds the RutrackerRepositoryImpl implementation to the RutrackerRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindRutrackerRepository(
        repository: com.jabook.app.jabook.compose.data.repository.RutrackerRepositoryImpl,
    ): com.jabook.app.jabook.compose.data.repository.RutrackerRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository,
    ): com.jabook.app.jabook.compose.data.preferences.SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSleepTimerRepository(
        impl: com.jabook.app.jabook.compose.data.repository.SleepTimerRepositoryImpl,
    ): com.jabook.app.jabook.compose.data.repository.SleepTimerRepository

    @Binds
    @Singleton
    abstract fun bindLocalBookScanner(
        impl: com.jabook.app.jabook.compose.data.local.scanner.MediaStoreBookScanner,
    ): com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner

    @Binds
    @Singleton
    abstract fun bindAudioMetadataParser(
        impl: com.jabook.app.jabook.compose.data.local.parser.Media3MetadataParser,
    ): com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser

    companion object {
        /**
         * Provides DataStore<Preferences> for user preferences.
         */
        @Provides
        @Singleton
        fun provideDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.dataStore

        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): androidx.work.WorkManager = androidx.work.WorkManager.getInstance(context)
    }
}
