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

import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.compose.core.logger.LogLevel
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing LoggerFactory.
 *
 * In debug builds, provides AndroidLoggerFactory with DEBUG level.
 * In release builds, provides NoOpLoggerFactory to disable logging.
 */
@Module
@InstallIn(SingletonComponent::class)
public object LoggerModule {
    @Provides
    @Singleton
    public fun provideLoggerFactory(): LoggerFactory =
        if (BuildConfig.DEBUG) {
            LoggerFactoryImpl(LogLevel.DEBUG)
        } else {
            NoOpLoggerFactory
        }
}
