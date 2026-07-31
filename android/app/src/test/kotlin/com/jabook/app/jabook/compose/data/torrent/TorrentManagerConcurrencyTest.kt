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

import android.content.Context
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TorrentManagerConcurrencyTest {
    @Test
    fun `concurrent initialization initializes native session once`() {
        val session = mockk<TorrentSession>()
        val downloads = MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())
        val initializationStarted = CountDownLatch(1)
        val allowInitializationToFinish = CountDownLatch(1)
        val initializationCalls = AtomicInteger(0)
        every { session.downloadsFlow } returns downloads
        every { session.initSession() } answers {
            initializationCalls.incrementAndGet()
            initializationStarted.countDown()
            allowInitializationToFinish.await(1, TimeUnit.SECONDS)
        }

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.userPreferences } returns emptyFlow()
        val networkMonitor = mockk<NetworkMonitor>()
        every { networkMonitor.networkType } returns emptyFlow()
        val manager =
            TorrentManager(
                context = mockk<Context>(relaxed = true),
                session = session,
                repository = mockk(relaxed = true),
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
        } finally {
            allowInitializationToFinish.countDown()
            executor.shutdownNow()
        }
    }

    private fun noOpLoggerFactory(): LoggerFactory {
        val logger = mockk<Logger>(relaxed = true)
        return object : LoggerFactory {
            override fun get(tag: String): Logger = logger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = logger
        }
    }
}
