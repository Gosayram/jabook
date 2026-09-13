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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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

    @Test
    fun `initialization after shutdown restores active downloads again`() {
        val session = mock<TorrentSession>()
        whenever(session.downloadsFlow).thenReturn(MutableStateFlow(emptyMap()))
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

        manager.initialize()
        manager.shutdown()
        manager.initialize()

        verify(session, times(2)).restoreActiveDownloads()
    }

    @Test
    fun `shutdown with current generation still stops session`() {
        val session = mock<TorrentSession>()
        whenever(session.downloadsFlow).thenReturn(MutableStateFlow(emptyMap()))
        val manager = newManager(session)

        manager.initialize()
        manager.shutdown()

        verify(session).stopSession()
    }

    @Test
    fun `stale shutdown with generation mismatch does not stop re-initialized session`() {
        val session = mock<TorrentSession>()
        whenever(session.downloadsFlow).thenReturn(MutableStateFlow(emptyMap()))
        whenever(session.addTorrent(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success("a".repeat(40)))
        val manager = newManager(session)

        manager.initialize()
        // User adds a torrent on the live session ("native add observed").
        assertTrue(manager.addTorrent("magnet:?xt=urn:btih:${"a".repeat(40)}", "/dl").isSuccess)

        // The old service instance captured the generation BEFORE launching its
        // detached shutdown; Android then recreated the service first.
        val staleGeneration = manager.currentGeneration
        manager.initialize()
        // The detached shutdown finally runs with the stale token.
        manager.shutdown(staleGeneration)

        // No teardown behind the new lifecycle's back: session never deleted, so
        // the add that landed before (and any after) can never hit a freed native
        // session.
        verify(session, never()).stopSession()
        assertTrue(manager.addTorrent("magnet:?xt=urn:btih:${"b".repeat(40)}", "/dl").isSuccess)
        verify(session, times(2)).addTorrent(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `delayed shutdown from previous lifecycle does not kill session created by later initialize`() {
        val session = mock<TorrentSession>()
        whenever(session.downloadsFlow).thenReturn(MutableStateFlow(emptyMap()))
        val manager = newManager(session)

        manager.initialize()
        // Detached shutdown is delayed (IO dispatcher); the recreated service's
        // onCreate wins the race and bumps the generation first.
        val staleGeneration = manager.currentGeneration
        manager.initialize()
        // First (stale) shutdown completes after the second initialize.
        manager.shutdown(staleGeneration)

        // Second session survives the first shutdown's completion.
        verify(session, never()).stopSession()
        assertTrue(manager.currentGeneration > staleGeneration)
    }

    private fun newManager(session: TorrentSession): TorrentManager {
        val settingsRepository = mock<SettingsRepository>()
        whenever(settingsRepository.userPreferences).thenReturn(emptyFlow())
        val networkMonitor = mock<NetworkMonitor>()
        whenever(networkMonitor.networkType).thenReturn(emptyFlow())
        return TorrentManager(
            context = mock(),
            session = session,
            repository = mock(),
            settingsRepository = settingsRepository,
            networkMonitor = networkMonitor,
            loggerFactory = noOpLoggerFactory(),
        )
    }

    private fun noOpLoggerFactory(): LoggerFactory {
        val logger = mock<Logger>()
        return object : LoggerFactory {
            override fun get(tag: String): Logger = logger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = logger
        }
    }
}
