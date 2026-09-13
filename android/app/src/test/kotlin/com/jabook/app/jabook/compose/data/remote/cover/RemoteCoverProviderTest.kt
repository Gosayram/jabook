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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class RemoteCoverProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: RemoteCoverProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString()
        // Provider upgrades http thumbnails to https; in tests we serve plain http,
        // so bounce https requests for the local mock host back to http.
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (request.url.isHttps && request.url.host == server.hostName) {
                        chain.proceed(
                            request
                                .newBuilder()
                                .url(
                                    request.url
                                        .newBuilder()
                                        .scheme("http")
                                        .build(),
                                ).build(),
                        )
                    } else {
                        chain.proceed(request)
                    }
                }.build()
        provider =
            RemoteCoverProvider(
                client,
                NoopLoggerFactory,
                olBaseUrl = base,
                googleBaseUrl = base,
                olCoverBaseUrl = base,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `openlibrary hit returns covers url`() =
        runBlocking {
            server.enqueue(jsonBody("""{"docs":[{"cover_i":12345,"title":"Война и мир","author_name":["Лев Толстой"]}]}"""))
            server.enqueue(jsonBody("""{"items":[]}"""))
            server.enqueue(portraitImage())

            assertEquals(
                server.url("/b/id/12345-L.jpg").toString(),
                provider.lookup("Война и мир", "Лев Толстой"),
            )
            assertEquals(3, server.requestCount)
            assertEquals(
                listOf("/search.json", "/books/v1/volumes", "/b/id/12345-L.jpg"),
                recordedPaths(3),
            )
        }

    @Test
    fun `openlibrary miss falls back to google and upgrades http thumbnail to https`() =
        runBlocking {
            server.enqueue(jsonBody("""{"docs":[{"title":"Другая книга","author_name":["Ктото Другой"]}]}"""))
            val thumbnailUrl = server.url("/content?id=x").toString()
            server.enqueue(
                jsonBody(
                    """{"items":[{"volumeInfo":{"title":"Мастер и Маргарита","authors":["Булгаков"],""" +
                        """"imageLinks":{"thumbnail":"$thumbnailUrl"}}}]}""",
                ),
            )
            server.enqueue(portraitImage())

            assertEquals(
                thumbnailUrl.replace("http://", "https://"),
                provider.lookup("Мастер и Маргарита", "Булгаков"),
            )
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `both sources miss returns null`() =
        runBlocking {
            server.enqueue(jsonBody("""{"docs":[]}"""))
            server.enqueue(jsonBody("""{"items":[]}"""))

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
                jsonBody(
                    """{"docs":[""" +
                        """{"cover_i":1,"title":"Совершенно другое произведение","author_name":["Ктото"]},""" +
                        """{"cover_i":2,"title":"Война и мир (том 1)","author_name":["Толстой"]}]}""",
                ),
            )
            server.enqueue(jsonBody("""{"items":[]}"""))
            server.enqueue(portraitImage())

            assertEquals(
                server.url("/b/id/2-L.jpg").toString(),
                provider.lookup("Война и мир", "Лев Толстой"),
            )
            assertEquals(
                listOf("/search.json", "/books/v1/volumes", "/b/id/2-L.jpg"),
                recordedPaths(3),
            )
        }

    @Test
    fun `higher scored candidate image is fetched first`() =
        runBlocking {
            server.enqueue(
                jsonBody(
                    """{"docs":[""" +
                        """{"cover_i":1,"title":"Война и мир (том 1)","author_name":["Толстой"]},""" +
                        """{"cover_i":2,"title":"Война и мир","author_name":["Лев Толстой"]}]}""",
                ),
            )
            server.enqueue(jsonBody("""{"items":[]}"""))
            server.enqueue(portraitImage())

            assertEquals(
                server.url("/b/id/2-L.jpg").toString(),
                provider.lookup("Война и мир", "Лев Толстой"),
            )
            // Exact match (score 1.0) outranks the partial one: only its image is fetched.
            assertEquals(
                listOf("/search.json", "/books/v1/volumes", "/b/id/2-L.jpg"),
                recordedPaths(3),
            )
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `tiny placeholder image falls through to the next candidate`() =
        runBlocking {
            server.enqueue(
                jsonBody(
                    """{"docs":[""" +
                        """{"cover_i":1,"title":"Война и мир","author_name":["Лев Толстой"]},""" +
                        """{"cover_i":2,"title":"Война и мир (том 1)","author_name":["Толстой"]}]}""",
                ),
            )
            server.enqueue(jsonBody("""{"items":[]}"""))
            // 1x1 tracking gif: plausible-looking candidate, useless image.
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/gif")
                    .setBody(
                        okio.Buffer().write(
                            Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"),
                        ),
                    ),
            )
            server.enqueue(portraitImage())

            assertEquals(
                server.url("/b/id/2-L.jpg").toString(),
                provider.lookup("Война и мир", "Лев Толстой"),
            )
            assertEquals(
                listOf("/search.json", "/books/v1/volumes", "/b/id/1-L.jpg", "/b/id/2-L.jpg"),
                recordedPaths(4),
            )
        }

    @Test
    fun `image download budget is capped at three`() =
        runBlocking {
            val docs = (1..4).joinToString(",") { """{"cover_i":$it,"title":"Война и мир","author_name":["Лев Толстой"]}""" }
            server.enqueue(jsonBody("""{"docs":[$docs]}"""))
            server.enqueue(jsonBody("""{"items":[]}"""))
            repeat(3) {
                server.enqueue(MockResponse().setBody(okio.Buffer().write(Random(1).nextBytes(4096))))
            }

            assertNull(provider.lookup("Война и мир", "Лев Толстой"))
            // 2 metadata GETs + 3 image GETs; the 4th candidate is never fetched.
            assertEquals(5, server.requestCount)
            assertEquals(
                listOf("/search.json", "/books/v1/volumes", "/b/id/1-L.jpg", "/b/id/2-L.jpg", "/b/id/3-L.jpg"),
                recordedPaths(5),
            )
        }

    /** Drains [count] recorded requests in order, query strings stripped. */
    private fun recordedPaths(count: Int): List<String> =
        List(count) {
            server
                .takeRequest()
                .path
                .orEmpty()
                .substringBefore('?')
        }

    private fun jsonBody(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun portraitImage(): MockResponse =
        MockResponse().setHeader("Content-Type", "image/jpeg").setBody(okio.Buffer().write(portraitJpeg()))

    private fun portraitJpeg(
        width: Int = 200,
        height: Int = 300,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.DKGRAY)
        // Scatter noise so the payload clears the minimum-byte threshold.
        val random = Random(7)
        repeat(width * height / 8) {
            bitmap.setPixel(
                random.nextInt(width),
                random.nextInt(height),
                Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)),
            )
        }
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        bitmap.recycle()
        return output.toByteArray()
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
