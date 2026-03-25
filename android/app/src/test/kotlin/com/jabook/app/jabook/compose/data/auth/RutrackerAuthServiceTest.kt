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
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
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
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val utf8: Charset = Charsets.UTF_8
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
            enqueueHtmlFixture("login_success.html")

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

    @Test
    fun `login returns invalid credentials error for fail fixture`() =
        runTest {
            enqueueHtmlFixture("login_fail.html")

            val result =
                authService.login(
                    credentials = UserCredentials(username = "test-user", password = "bad-pass"),
                )

            assertEquals(
                RutrackerAuthService.AuthResult.Error("Invalid username or password"),
                result,
            )
            assertEquals("Invalid username or password", authService.lastAuthError)
        }

    @Test
    fun `login returns captcha result when captcha challenge is present`() =
        runTest {
            enqueueHtmlFixture("login_captcha.html")

            val result =
                authService.login(
                    credentials = UserCredentials(username = "test-user", password = "test-pass"),
                )

            assertTrue(result is RutrackerAuthService.AuthResult.Captcha)
            val captcha = result as RutrackerAuthService.AuthResult.Captcha
            assertEquals("123456", captcha.data.sid)
            assertTrue(captcha.data.url.contains("captcha"))
            assertNull(authService.lastAuthError)
        }

    @Test
    fun `validateAuth returns false when all validation probes show session expired`() =
        runTest {
            enqueueHtmlFixture("login_session_expired.html")
            enqueueHtmlFixture("login_session_expired.html")
            enqueueHtmlFixture("login_session_expired.html")

            val result = authService.validateAuth()

            assertFalse(result)
            val profileRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
            val searchRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
            val indexRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
            assertNotNull(profileRequest)
            assertNotNull(searchRequest)
            assertNotNull(indexRequest)
            assertEquals("/forum/profile.php?mode=viewprofile", profileRequest?.path)
            assertTrue(searchRequest?.path?.startsWith("/forum/tracker.php?nm=test&f=33") == true)
            assertEquals("/forum/index.php", indexRequest?.path)
        }

    @Test
    fun `login retries transient network failures and succeeds on final attempt`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
            )
            enqueueHtmlFixture("login_success.html")

            val result =
                authService.login(
                    credentials = UserCredentials(username = "retry-user", password = "retry-pass"),
                )

            assertEquals(RutrackerAuthService.AuthResult.Success, result)
            assertNull(authService.lastAuthError)
            assertEquals(3, mockWebServer.requestCount)
        }

    @Test
    fun `login returns no connection error after exhausting retry attempts`() =
        runTest {
            repeat(3) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
                )
            }

            val result =
                authService.login(
                    credentials = UserCredentials(username = "timeout-user", password = "timeout-pass"),
                )

            assertEquals(
                RutrackerAuthService.AuthResult.Error(RuTrackerError.NoConnection.message),
                result,
            )
            assertEquals(RuTrackerError.NoConnection.message, authService.lastAuthError)
            assertEquals(3, mockWebServer.requestCount)
        }

    private fun createApi(): RutrackerApi =
        Retrofit
            .Builder()
            .baseUrl(mockWebServer.url("/forum/"))
            .client(OkHttpClient())
            .build()
            .create(RutrackerApi::class.java)

    private fun enqueueHtmlFixture(fileName: String) {
        val bodyBytes = loadFixture(fileName).toByteArray(cp1251)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=windows-1251")
                .setBody(Buffer().write(bodyBytes)),
        )
    }

    private fun loadFixture(fileName: String): String {
        val resourcePath = "fixtures/rutracker/$fileName"
        val stream =
            checkNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
                "Fixture not found: $resourcePath"
            }
        return stream.bufferedReader(utf8).use { it.readText() }
    }
}
