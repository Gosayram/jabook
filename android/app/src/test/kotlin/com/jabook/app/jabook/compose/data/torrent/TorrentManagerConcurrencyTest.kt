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

package com.jabook.app.jabook.compose.data.torrent

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TorrentManagerConcurrencyTest {
    @Test
    fun `concurrent initialization restores active downloads once`() {
        val session = mock<TorrentSession>()
        val downloads = MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())
        val initializationStarted = CountDownLatch(1)
        val allowInitializationToFinish = CountDownLatch(1)
        val initializationCalls = AtomicInteger(0)
        whenever(session.downloadsFlow).thenReturn(downloads)
        doAnswer {
            initializationCalls.incrementAndGet()
            initializationStarted.countDown()
            allowInitializationToFinish.await(1, TimeUnit.SECONDS)
        }.whenever(session).initSession()

        val settingsRepository = mock<SettingsRepository>()
        whenever(settingsRepository.userPreferences).thenReturn(emptyFlow())
        val networkMonitor = mock<NetworkMonitor>()
        whenever(networkMonitor.networkType).thenReturn(emptyFlow())
        val manager =
            TorrentManager(
                context = mock(),
                session = session,
                repository = mock(),
                settingsRepository = settingsRepository,
                networkMonitor = networkMonitor,
                loggerFactory = noOpLoggerFactory(),
            )

        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { manager.initialize() }
            assertTrue(initializationStarted.await(1, TimeUnit.SECONDS))
            val second = executor.submit { manager.initialize() }

            assertEquals("A second caller must wait for the in-progress initialization", 1, initializationCalls.get())

            allowInitializationToFinish.countDown()
            first.get(1, TimeUnit.SECONDS)
            second.get(1, TimeUnit.SECONDS)
            verify(session).restoreActiveDownloads()
        } finally {
            allowInitializationToFinish.countDown()
            executor.shutdownNow()
        }
    }

    private fun noOpLoggerFactory(): LoggerFactory {
        val logger = mock<Logger>()
        return object : LoggerFactory {
            override fun get(tag: String): Logger = logger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = logger
        }
    }
}
