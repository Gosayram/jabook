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

package com.jabook.app.jabook.compose.data.remote.cover

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RemoteCoverProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: RemoteCoverProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString()
        provider = RemoteCoverProvider(OkHttpClient(), NoopLoggerFactory, olBaseUrl = base, googleBaseUrl = base)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `openlibrary hit returns covers url`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"docs":[{"cover_i":12345,"title":"Война и мир","author_name":["Лев Толстой"]}]}"""),
            )

            assertEquals(
                "https://covers.openlibrary.org/b/id/12345-L.jpg",
                provider.lookup("Война и мир", "Лев Толстой"),
            )
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `openlibrary miss falls back to google and upgrades http thumbnail to https`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"docs":[{"title":"Другая книга","author_name":["Ктото Другой"]}]}"""),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"items":[{"volumeInfo":{"title":"Мастер и Маргарита","authors":["Булгаков"],""" +
                            """"imageLinks":{"thumbnail":"http://books.google.com/content?id=x"}}}]}""",
                    ),
            )

            assertEquals(
                "https://books.google.com/content?id=x",
                provider.lookup("Мастер и Маргарита", "Булгаков"),
            )
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `both sources miss returns null`() =
        runBlocking {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody("""{"docs":[]}"""),
            )
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[]}"""),
            )

            assertNull(provider.lookup("Незнакомая книга", "Незнакомый Автор"))
        }

    @Test
    fun `network errors never throw and return null`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            assertNull(provider.lookup("Война и мир", "Лев Толстой"))
        }

    @Test
    fun `http error responses are treated as a miss`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(404))

            assertNull(provider.lookup("Война и мир", "Лев Толстой"))
        }

    @Test
    fun `inaccurate candidate is skipped in favour of the next one`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"docs":[""" +
                            """{"cover_i":1,"title":"Совершенно другое произведение","author_name":["Ктото"]},""" +
                            """{"cover_i":2,"title":"Война и мир (том 1)","author_name":["Толстой"]}]}""",
                    ),
            )

            assertEquals(
                "https://covers.openlibrary.org/b/id/2-L.jpg",
                provider.lookup("Война и мир", "Лев Толстой"),
            )
        }

    private object NoopLoggerFactory : com.jabook.app.jabook.compose.core.logger.LoggerFactory {
        override fun get(tag: String): com.jabook.app.jabook.compose.core.logger.Logger = NoopLogger

        override fun get(clazz: kotlin.reflect.KClass<*>): com.jabook.app.jabook.compose.core.logger.Logger = NoopLogger
    }

    private object NoopLogger : com.jabook.app.jabook.compose.core.logger.Logger {
        override fun d(message: () -> String) = Unit

        override fun d(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun e(message: () -> String) = Unit

        override fun e(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun i(message: () -> String) = Unit

        override fun i(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun w(message: () -> String) = Unit

        override fun w(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun v(message: () -> String) = Unit

        override fun v(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun v(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit
    }
}
