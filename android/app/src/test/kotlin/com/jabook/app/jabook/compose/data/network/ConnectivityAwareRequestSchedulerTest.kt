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

package com.jabook.app.jabook.compose.data.network

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityAwareRequestSchedulerTest {
    @Test
    fun `awaitOnline returns true immediately when already online`() =
        runTest {
            val monitor = FakeNetworkMonitor(initialOnline = true)
            val scheduler = ConnectivityAwareRequestScheduler(monitor, NoOpLoggerFactory)

            val result = scheduler.awaitOnline(operation = "test")

            assertTrue(result)
        }

    @Test
    fun `awaitOnline resumes when connectivity is restored`() =
        runTest {
            val monitor = FakeNetworkMonitor(initialOnline = false)
            val scheduler = ConnectivityAwareRequestScheduler(monitor, NoOpLoggerFactory)

            val deferred = async { scheduler.awaitOnline(operation = "topic", timeoutMs = 5_000L) }
            monitor.setOnline(true)

            assertTrue(deferred.await())
        }

    @Test
    fun `awaitOnline returns false on timeout`() =
        runTest {
            val monitor = FakeNetworkMonitor(initialOnline = false)
            val scheduler = ConnectivityAwareRequestScheduler(monitor, NoOpLoggerFactory)

            val deferred = async { scheduler.awaitOnline(operation = "topic", timeoutMs = 1_000L) }
            advanceTimeBy(1_100L)

            assertFalse(deferred.await())
        }
}

private class FakeNetworkMonitor(
    initialOnline: Boolean,
) : NetworkMonitor {
    private val networkTypeState = MutableStateFlow(if (initialOnline) NetworkType.WIFI else NetworkType.NONE)

    override val networkType: Flow<NetworkType> = networkTypeState
    override val isOnline: Flow<Boolean> = networkTypeState.map { it != NetworkType.NONE }

    fun setOnline(online: Boolean) {
        networkTypeState.value = if (online) NetworkType.WIFI else NetworkType.NONE
    }
}
