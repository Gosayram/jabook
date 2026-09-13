// Copyright 2026 Jabook Contributors
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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Backing store for media3's DownloadManager index (file-based, so pre-cache progress and
 * resume state survive app kills). StandaloneDatabaseProvider is media3's own "shared SQLite
 * file for cache + download index" provider — the same one MediaModule's SimpleCache uses,
 * which is the documented way to run both over one database (tables are created idempotently).
 */
@Module
@InstallIn(SingletonComponent::class)
public object OfflineModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    public fun provideOfflineDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)
}
