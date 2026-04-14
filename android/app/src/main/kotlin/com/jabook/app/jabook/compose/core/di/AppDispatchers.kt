/**
 * Copyright (c) 2025 JaBook Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jabook.app.jabook.compose.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Injectable coroutine dispatchers for testability.
 *
 * ## Problem
 * Hardcoded `Dispatchers.IO/Default/Main` makes code untestable:
 * - Cannot replace with `TestDispatcher` in unit tests
 * - Tests become flaky due to real thread scheduling
 * - Cannot control execution order in tests
 *
 * ## Solution
 * Inject `AppDispatchers` interface and use `dispatchers.io` instead of `Dispatchers.IO`.
 * In tests, provide `TestDispatchers` with `UnconfinedTestDispatcher` or `StandardTestDispatcher`.
 *
 * ## Usage
 * ```kotlin
 * class MyRepository @Inject constructor(
 *     private val dispatchers: AppDispatchers,
 * ) {
 *     suspend fun loadData(): Data = withContext(dispatchers.io) {
 *         // IO operation
 *     }
 * }
 * ```
 *
 * ## Migration Status
 * - 155 places with hardcoded `Dispatchers.*` found
 * - Migrate incrementally, starting with new code and critical paths
 * - Add `audit-dispatchers` make target to track progress
 */
public interface AppDispatchers {
    /** For IO operations: network, database, file system */
    public val io: CoroutineDispatcher

    /** For CPU-intensive work: sorting, parsing, computation */
    public val default: CoroutineDispatcher

    /** For UI updates and main-thread operations */
    public val main: CoroutineDispatcher

    /** For unconfined execution (use sparingly, mainly for testing) */
    public val unconfined: CoroutineDispatcher
}

/**
 * Production implementation using real Kotlin dispatchers.
 */
@Singleton
public class ProductionDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

/**
 * Hilt module for providing AppDispatchers.
 */
@Module
@InstallIn(SingletonComponent::class)
public object DispatchersModule {

    @Provides
    @Singleton
    public fun provideAppDispatchers(): AppDispatchers = ProductionDispatchers()
}