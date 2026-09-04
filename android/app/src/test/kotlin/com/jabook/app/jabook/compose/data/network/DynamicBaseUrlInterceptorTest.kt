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
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for DynamicBaseUrlInterceptor mirror failover.
 *
 * Uses a REAL MirrorManager (FakeSettingsRepository + stub health-check client)
 * so the interceptor's sync/async contract with MirrorManager is exercised
 * end-to-end. The upstream is a MockWebServer; DNS is pinned to loopback and
 * requests are routed by Host header, emulating two mirror domains.
 */
@RunWith(RobolectricTestRunner::class)
class DynamicBaseUrlInterceptorTest {
    // Both hosts must contain "rutracker" — that's the interceptor's trigger.
    private val mirrorA = "mirror-a.rutracker.test"
    private val mirrorB = "mirror-b.rutracker.test"

    private lateinit var server: MockWebServer

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun `500 with successful mirror switch retries on new mirror and returns retry response`() {
        val env = startEnvironment(autoSwitchEnabled = true, customMirrors = listOf(mirrorB), healthyHosts = setOf(mirrorB))
        env.awaitMirrorSettled()
        env.serverDispatcher.successHost = mirrorB

        val response = env.sendRequest()

        assertEquals(200, response.code)
        assertEquals("ok", response.body.string())
        assertEquals(listOf(mirrorA, mirrorB), env.serverDispatcher.receivedHosts.toList())
        assertTrue(env.healthProbes.get() > 0)
        assertEquals(mirrorB, env.mirrorManager.getCurrentMirrorDomain())
    }

    @Test
    fun `500 with failed switch attempt returns original open response with readable body`() {
        val env = startEnvironment(autoSwitchEnabled = true, customMirrors = listOf(mirrorB), healthyHosts = emptySet())

        val response = env.sendRequest()

        // Locks the closed-response regression: the original 500 must come back
        // OPEN with its body readable — reading a closed body throws.
        assertEquals(500, response.code)
        assertEquals("boom", response.body.string())
        assertEquals(listOf(mirrorA), env.serverDispatcher.receivedHosts.toList())
        assertTrue(env.healthProbes.get() > 0)
    }

    @Test
    fun `grace or backoff window returns error immediately without blocking on switch`() {
        val env = startEnvironment(autoSwitchEnabled = true, customMirrors = listOf(mirrorB), healthyHosts = emptySet())
        env.awaitMirrorSettled()

        // Put the manager into failure backoff: all mirrors dead → failed switch.
        runBlocking { assertFalse(env.mirrorManager.switchToNextMirror()) }
        assertFalse(env.mirrorManager.canSwitchNowSync())
        val probesBeforeRequest = env.healthProbes.get()

        val response = env.sendRequest()

        assertEquals(500, response.code)
        assertEquals("boom", response.body.string())
        // Exactly one upstream hit and zero new health probes: the canSwitchNowSync
        // gate must fire BEFORE any (blocking) switch work.
        assertEquals(listOf(mirrorA), env.serverDispatcher.receivedHosts.toList())
        assertEquals(probesBeforeRequest.toLong(), env.healthProbes.get().toLong())
    }

    @Test
    fun `auto-switch disabled passes error through without any switch attempt`() {
        val env = startEnvironment(autoSwitchEnabled = false, customMirrors = listOf(mirrorB), healthyHosts = setOf(mirrorB))
        env.awaitMirrorSettled()

        val response = env.sendRequest()

        assertEquals(500, response.code)
        assertEquals("boom", response.body.string())
        assertEquals(listOf(mirrorA), env.serverDispatcher.receivedHosts.toList())
        assertEquals(0, env.healthProbes.get())
        assertEquals(mirrorA, env.mirrorManager.getCurrentMirrorDomain())
    }

    private inner class Environment(
        val mirrorManager: MirrorManager,
        val healthProbes: AtomicInteger,
        private val client: OkHttpClient,
    ) {
        val serverDispatcher: HostRoutingDispatcher get() = server.dispatcher as HostRoutingDispatcher

        /** The init collector applies saved prefs async — wait until it settles. */
        fun awaitMirrorSettled() {
            awaitCondition("mirror=$mirrorA loaded") {
                mirrorManager.getCurrentMirrorDomain() == mirrorA
            }
        }

        fun sendRequest(): Response =
            client
                .newBuilder()
                .addInterceptor(DynamicBaseUrlInterceptor(mirrorManager, NoOpLoggerFactory))
                .build()
                .newCall(
                    Request
                        .Builder()
                        .url("http://$mirrorA:${server.port}/forum/index.php")
                        .build(),
                ).execute()
    }

    private fun startEnvironment(
        autoSwitchEnabled: Boolean,
        customMirrors: List<String>,
        healthyHosts: Set<String>,
    ): Environment {
        val dispatcher = HostRoutingDispatcher(successHost = null)
        server = MockWebServer()
        server.dispatcher = dispatcher
        server.start()
        val probeCount = AtomicInteger(0)
        val healthCheckClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    probeCount.incrementAndGet()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (chain.request().url.host in healthyHosts) 200 else 503)
                        .message("stub")
                        .body("{}".toResponseBody())
                        .build()
                }.build()
        val settingsRepository =
            FakeSettingsRepository(
                initial =
                    UserPreferences
                        .newBuilder()
                        .setSelectedMirror(mirrorA)
                        .setAutoSwitchMirror(autoSwitchEnabled)
                        .addAllCustomMirrors(customMirrors)
                        .build(),
            )
        val mirrorManager =
            MirrorManager(
                settingsRepository = settingsRepository,
                okHttpClient = healthCheckClient,
                loggerFactory = NoOpLoggerFactory,
            )
        val baseClient =
            OkHttpClient
                .Builder()
                .dns(Dns { listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) })
                .build()
        return Environment(mirrorManager, probeCount, baseClient)
    }

    private fun awaitCondition(
        description: String,
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for $description")
            }
            java.util.concurrent.locks.LockSupport
                .parkNanos(10_000_000L)
        }
    }

    /** Serves 200 only for [successHost]; everything else gets a plain 500. */
    private class HostRoutingDispatcher(
        var successHost: String?,
    ) : Dispatcher() {
        val receivedHosts: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun dispatch(request: RecordedRequest): MockResponse {
            val host = request.headers["Host"]?.substringBefore(":").orEmpty()
            receivedHosts += host
            return if (host == successHost) {
                MockResponse().setResponseCode(200).setBody("ok")
            } else {
                MockResponse().setResponseCode(500).setBody("boom")
            }
        }
    }
}
