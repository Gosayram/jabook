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

package com.jabook.app.jabook.compose.data.auth

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.encoding.RutrackerSimpleDecoder
import com.jabook.app.jabook.compose.data.remote.parser.CoverUrlExtractor
import com.jabook.app.jabook.compose.data.remote.parser.DefensiveFieldExtractor
import com.jabook.app.jabook.compose.data.remote.parser.MediaInfoParser
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Retrofit
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RutrackerAuthServiceTest {
    private val cp1251: Charset = Charset.forName("windows-1251")
    private lateinit var mockWebServer: MockWebServer
    private lateinit var authService: RutrackerAuthService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val api = createApi()
        val mirrorManager: MirrorManager = mock()
        whenever(mirrorManager.getBaseUrl()).thenReturn(mockWebServer.url("/").toString().removeSuffix("/"))

        val parser =
            RutrackerParser(
                mediaInfoParser = mock<MediaInfoParser>(),
                decoder = mock<RutrackerSimpleDecoder>(),
                fieldExtractor = mock<DefensiveFieldExtractor>(),
                coverExtractor = mock<CoverUrlExtractor>(),
                mirrorManager = mirrorManager,
                loggerFactory = NoOpLoggerFactory,
            )
        val decoder = RutrackerSimpleDecoder(loggerFactory = NoOpLoggerFactory)

        authService =
            RutrackerAuthService(
                api = api,
                parser = parser,
                decoder = decoder,
                loggerFactory = NoOpLoggerFactory,
            )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `login returns success for valid login fixture response`() =
        runTest {
            val bodyBytes = loadFixture("login_success.html").toByteArray(cp1251)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=windows-1251")
                    .setBody(Buffer().write(bodyBytes)),
            )

            val result =
                authService.login(
                    credentials = UserCredentials(username = "test-user", password = "test-pass"),
                )

            assertEquals(RutrackerAuthService.AuthResult.Success, result)
            assertNull(authService.lastAuthError)

            val request = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
            assertNotNull(request)
            request!!
            assertEquals("POST", request.method)
            assertEquals("/forum/login.php", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("login_username="))
            assertTrue(body.contains("login_password="))
            assertTrue(body.contains("login="))
        }

    private fun createApi(): RutrackerApi =
        Retrofit
            .Builder()
            .baseUrl(mockWebServer.url("/forum/"))
            .client(OkHttpClient())
            .build()
            .create(RutrackerApi::class.java)

    private fun loadFixture(fileName: String): String {
        val resourcePath = "fixtures/rutracker/$fileName"
        val stream =
            checkNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
                "Fixture not found: $resourcePath"
            }
        return stream.bufferedReader(cp1251).use { it.readText() }
    }
}
