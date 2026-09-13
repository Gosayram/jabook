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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Regression tests for TorrentSessionManager.stopSession() locking.
 *
 * The vendored libtorrent4j SessionManager.stop() nulls its session, sleeps
 * ~750ms, then joins the alert loop WITHOUT a timeout; alert handlers call the
 * @Synchronized updateDownloads() which needs TorrentSessionManager's class
 * monitor. stopSession() must therefore run session.stop() OUTSIDE that
 * monitor — the old code held it and deadlocked (ANR from main-thread callers).
 */
class TorrentSessionManagerStopSessionTest {
    @Test(timeout = 10_000)
    fun `stopSession does not deadlock when native stop joins an alert thread blocked on the class monitor`() {
        assumeNativeLibtorrentAvailable()
        val alertThreadEnteredMonitor = CountDownLatch(1)
        val managerRef =
            java.util.concurrent.atomic
                .AtomicReference<TorrentSessionManager>()
        val manager =
            newManagerWithInjectedSession(
                StubSessionManager {
                    val alertThread =
                        Thread {
                            // updateDownloads is private; pauseAll() takes the same class monitor.
                            managerRef.get().pauseAll()
                            alertThreadEnteredMonitor.countDown()
                        }
                    alertThread.start()
                    alertThread.join()
                },
            )
        managerRef.set(manager)

        val startNanos = System.nanoTime()
        manager.stopSession()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

        // The joined alert thread must have acquired the monitor — proves stop()
        // ran outside it (pre-fix this latch never opens and stopSession hangs).
        assertTrue("alert thread never acquired the class monitor", alertThreadEnteredMonitor.await(5, TimeUnit.SECONDS))
        assertTrue("stopSession blocked for ${elapsedMs}ms (deadlock/ANR)", elapsedMs < 5_000)
    }

    @Test(timeout = 10_000)
    fun `stopSession clears session field before native stop and survives native stop throwing`() {
        assumeNativeLibtorrentAvailable()
        val stopCalled = AtomicBoolean(false)
        val manager =
            newManagerWithInjectedSession(
                StubSessionManager {
                    stopCalled.set(true)
                    throw RuntimeException("native teardown failure")
                },
            )

        manager.stopSession()

        assertTrue("native stop() must still be invoked", stopCalled.get())
        assertNull("session field must be cleared before native stop runs", injectedSessionOrNull(manager))
    }

    /**
     * Builds a TorrentSessionManager with a mocked SessionManager injected into
     * the private `session` field (initSession() needs the native library, which
     * JVM unit tests don't have).
     */
    private fun newManagerWithInjectedSession(stubSession: StubSessionManager): TorrentSessionManager {
        val manager =
            TorrentSessionManager(
                context = mock<Context>(),
                loggerFactory = noOpLoggerFactory(),
                torrentDownloadDao = mock(),
                torrentResumeDao = mock(),
                stateBuilder = mock(),
            )
        val sessionField = TorrentSessionManager::class.java.getDeclaredField("session")
        sessionField.isAccessible = true
        sessionField.set(manager, stubSession)
        return manager
    }

    /**
     * These tests are NATIVE-DEPENDENT: touching SessionManager (subclassing or
     * mocking) runs its <clinit>, which loads the libtorrent4j native library —
     * absent on desktop JVMs. Assume-guarded: they run wherever the native
     * binary exists, skip elsewhere.
     */
    private fun assumeNativeLibtorrentAvailable() {
        val usable =
            runCatching {
                org.libtorrent4j.SessionManager().let { /* constructed => native clinit OK */ }
                true
            }.getOrDefault(false)
        Assume.assumeTrue("libtorrent4j native binary not available on this JVM", usable)
    }

    /**
     * Hand-written stub instead of a Mockito mock: Mockito's Objenesis path also
     * fails once SessionManager's <clinit> loads the native library (see
     * [assumeNativeLibtorrentAvailable]).
     */
    private class StubSessionManager(
        private val onStop: () -> Unit,
    ) : org.libtorrent4j.SessionManager() {
        override fun stop() {
            onStop()
        }
    }

    private fun injectedSessionOrNull(manager: TorrentSessionManager): org.libtorrent4j.SessionManager? {
        val sessionField = TorrentSessionManager::class.java.getDeclaredField("session")
        sessionField.isAccessible = true
        return sessionField.get(manager) as org.libtorrent4j.SessionManager?
    }

    private fun noOpLoggerFactory(): LoggerFactory {
        val logger = mock<Logger>()
        return object : LoggerFactory {
            override fun get(tag: String): Logger = logger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = logger
        }
    }
}
