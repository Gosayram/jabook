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

package com.jabook.app.jabook.audio

import android.net.Uri
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [RemoteSourceRevalidator] (HEAD re-validation of cached
 * remote URLs with TTL memoisation and fail-open UNKNOWN semantics).
 */
@RunWith(RobolectricTestRunner::class)
class RemoteSourceRevalidatorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createRevalidator(nowMs: () -> Long = System::currentTimeMillis): RemoteSourceRevalidator =
        RemoteSourceRevalidator(okHttpClient = client, ttlMs = 60_000L, nowMs = nowMs)

    private fun url(path: String = "/chapter.mp3"): String = server.url(path).toString()

    @Test
    fun `HTTP 200 is ALIVE`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200))
            val revalidator = createRevalidator()
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(Uri.parse(url())))
        }

    @Test
    fun `HTTP redirect 302 is ALIVE`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(302))
            val revalidator = createRevalidator()
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(Uri.parse(url())))
        }

    @Test
    fun `HTTP 404 is DEAD`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))
            val revalidator = createRevalidator()
            assertEquals(RemoteSourceStatus.DEAD, revalidator.revalidate(Uri.parse(url())))
        }

    @Test
    fun `HTTP 500 is DEAD`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))
            val revalidator = createRevalidator()
            assertEquals(RemoteSourceStatus.DEAD, revalidator.revalidate(Uri.parse(url())))
        }

    @Test
    fun `connection drop is UNKNOWN fail-open`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val revalidator = createRevalidator()
            assertEquals(RemoteSourceStatus.UNKNOWN, revalidator.revalidate(Uri.parse(url())))
        }

    @Test
    fun `HEAD request is used not GET`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200))
            val revalidator = createRevalidator()
            revalidator.revalidate(Uri.parse(url()))
            val recorded = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("HEAD", recorded?.method)
        }

    @Test
    fun `second revalidation within TTL does not hit server`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200))
            val revalidator = createRevalidator()
            val uri = Uri.parse(url())
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(uri))
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(uri))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `TTL expiry re-HEADs the server`() =
        runBlocking {
            var now = 0L
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(404))
            val revalidator = createRevalidator(nowMs = { now })
            val uri = Uri.parse(url())
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(uri))
            now = 61_000L // past the 60s TTL
            assertEquals(RemoteSourceStatus.DEAD, revalidator.revalidate(uri))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `UNKNOWN result is memoised within TTL so replays do not thrash`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val revalidator = createRevalidator()
            val uri = Uri.parse(url())
            assertEquals(RemoteSourceStatus.UNKNOWN, revalidator.revalidate(uri))
            assertEquals(RemoteSourceStatus.UNKNOWN, revalidator.revalidate(uri))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `non-http scheme is ALIVE without any request`() =
        runBlocking {
            val revalidator = createRevalidator()
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(Uri.parse("file:///audio/ch1.m4b")))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `clearCache forces a fresh HEAD`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(404))
            val revalidator = createRevalidator()
            val uri = Uri.parse(url())
            assertEquals(RemoteSourceStatus.ALIVE, revalidator.revalidate(uri))
            revalidator.clearCache()
            assertEquals(RemoteSourceStatus.DEAD, revalidator.revalidate(uri))
            assertEquals(2, server.requestCount)
        }
}
