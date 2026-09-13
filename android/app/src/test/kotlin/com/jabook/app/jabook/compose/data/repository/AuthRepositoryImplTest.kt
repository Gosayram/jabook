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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.core.di.AppDispatchers
import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import com.jabook.app.jabook.compose.data.auth.CookiePersistenceManager
import com.jabook.app.jabook.compose.data.auth.RutrackerAuthService
import com.jabook.app.jabook.compose.data.auth.SecureCredentialStorage
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthRepositoryImplTest {
    private val authService: RutrackerAuthService = mock()
    private val secureStorage: SecureCredentialStorage = mock()
    private val cookieJar: PersistentCookieJar = mock()
    private val mirrorManager: MirrorManager = mock()
    private val cookiePersistence: CookiePersistenceManager = mock()
    private val preferencesRepository: UserPreferencesRepository = mock()

    @Test
    fun `validated WebView session authenticates with nickname from single index request`() =
        runTest {
            stubDefaults()
            whenever(cookieJar.loadForRequest(any())).thenReturn(listOf(sessionCookie()))
            whenever(secureStorage.getCredentials()).thenReturn(null)
            whenever(authService.fetchIndexAuthState())
                .thenReturn(RutrackerAuthService.IndexAuthState(loggedIn = true, username = "atlet99"))

            val repository = repository(UnconfinedTestDispatcher(testScheduler))

            assertTrue(repository.isLoggedIn())
            val status = repository.authStatus.value
            assertTrue(status is AuthStatus.Authenticated)
            assertEquals("atlet99", (status as AuthStatus.Authenticated).username)
            verify(preferencesRepository, atLeastOnce()).setAuthUsername("atlet99")
            verify(cookieJar, never()).clear()
        }

    @Test
    fun `invalid WebView session is rejected and cleared`() =
        runTest {
            stubDefaults()
            whenever(cookieJar.loadForRequest(any())).thenReturn(listOf(sessionCookie()))
            whenever(secureStorage.getCredentials()).thenReturn(null)
            whenever(authService.fetchIndexAuthState())
                .thenReturn(RutrackerAuthService.IndexAuthState(loggedIn = false, username = null))

            val repository = repository(UnconfinedTestDispatcher(testScheduler))

            assertFalse(repository.isLoggedIn())
            verify(cookieJar, atLeastOnce()).clear()
        }

    @Test
    fun `login succeeds immediately when POST parse succeeds without validation round-trip`() =
        runTest {
            stubDefaults()
            whenever(cookieJar.loadForRequest(any())).thenReturn(emptyList())
            whenever(authService.login(UserCredentials("user", "password"))).thenReturn(RutrackerAuthService.AuthResult.Success)

            val repository = repository(StandardTestDispatcher(testScheduler))

            val result = repository.login(UserCredentials("user", "password"))

            assertEquals(Result.success(true), result)
            val status = repository.authStatus.value
            assertTrue(status is AuthStatus.Authenticated)
            assertEquals("user", (status as AuthStatus.Authenticated).username)
            verify(preferencesRepository).setAuthUsername("user")
            // Validation must not block the critical path — the background check
            // only runs once the scheduler advances past the login call.
            verify(authService, never()).fetchIndexAuthState()
        }

    @Test
    fun `nickname fetched in background is persisted and emitted`() =
        runTest {
            stubDefaults()
            whenever(cookieJar.loadForRequest(any())).thenReturn(listOf(sessionCookie()))
            whenever(secureStorage.getCredentials()).thenReturn(null)
            whenever(preferencesRepository.getAuthUsername()).thenReturn("")
            whenever(authService.fetchIndexAuthState())
                .thenReturn(RutrackerAuthService.IndexAuthState(loggedIn = true, username = "atlet99"))

            val repository = repository(UnconfinedTestDispatcher(testScheduler))
            repository.syncCookiesFromWebView()

            val status = repository.authStatus.value
            assertTrue(status is AuthStatus.Authenticated)
            assertEquals("atlet99", (status as AuthStatus.Authenticated).username)
            verify(preferencesRepository, atLeastOnce()).setAuthUsername("atlet99")
        }

    @Test
    fun `literal User is never persisted or emitted when server reports it`() =
        runTest {
            stubDefaults()
            whenever(cookieJar.loadForRequest(any())).thenReturn(listOf(sessionCookie()))
            whenever(secureStorage.getCredentials()).thenReturn(null)
            whenever(preferencesRepository.getAuthUsername()).thenReturn("")
            whenever(authService.fetchIndexAuthState())
                .thenReturn(RutrackerAuthService.IndexAuthState(loggedIn = true, username = "User"))

            val repository = repository(UnconfinedTestDispatcher(testScheduler))
            repository.syncCookiesFromWebView()

            val status = repository.authStatus.value
            assertTrue(status is AuthStatus.Authenticated)
            assertFalse("User" == (status as AuthStatus.Authenticated).username)
            verify(preferencesRepository, never()).setAuthUsername(any())
        }

    @Test
    fun `login waits for in-flight startup relogin instead of failing instantly`() =
        runTest {
            stubDefaults()
            // Startup finds an invalid session with stored credentials and starts
            // an auto-relogin, which holds the login mutex while the POST is in flight.
            var indexCalls = 0
            whenever(authService.fetchIndexAuthState()).thenAnswer {
                indexCalls++
                if (indexCalls == 1) {
                    RutrackerAuthService.IndexAuthState(loggedIn = false, username = null)
                } else {
                    RutrackerAuthService.IndexAuthState(loggedIn = true, username = "atlet99")
                }
            }
            whenever(secureStorage.getCredentials()).thenReturn(UserCredentials("stored", "pass"))
            val loginGate = CompletableDeferred<RutrackerAuthService.AuthResult>()
            whenever(authService.login(UserCredentials("stored", "pass")))
                .doSuspendableAnswer { loginGate.await() }
            whenever(authService.login(UserCredentials("user", "password")))
                .thenReturn(RutrackerAuthService.AuthResult.Success)

            val repository = repository(StandardTestDispatcher(testScheduler))
            runCurrent()
            // Auto-relogin in progress and holding the login mutex.
            verify(authService).login(UserCredentials("stored", "pass"))

            val userLogin = async { repository.login(UserCredentials("user", "password")) }
            runCurrent()
            // Mutex is contended: login must be waiting, not instantly failed.
            assertTrue(userLogin.isActive)

            loginGate.complete(RutrackerAuthService.AuthResult.Success)
            advanceUntilIdle()

            assertEquals(Result.success(true), userLogin.await())
            val status = repository.authStatus.value
            assertTrue(status is AuthStatus.Authenticated)
            assertEquals("atlet99", (status as AuthStatus.Authenticated).username)
        }

    @Test
    fun `login switches an untrusted mirror before sending credentials`() =
        runTest {
            whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow("evil.example"))
            whenever(cookieJar.loadForRequest(any())).thenReturn(emptyList())
            whenever(authService.login(UserCredentials("user", "password"))).thenReturn(RutrackerAuthService.AuthResult.Success)

            val repository = repository(UnconfinedTestDispatcher(testScheduler))
            repository.login(UserCredentials("user", "password"))

            verify(mirrorManager).setMirror(MirrorManager.DEFAULT_MIRRORS.first())
        }

    private suspend fun stubDefaults() {
        whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow("mirror.example"))
        whenever(preferencesRepository.getAuthUsername()).thenReturn("")
    }

    private fun repository(dispatcher: CoroutineDispatcher): AuthRepositoryImpl =
        AuthRepositoryImpl(
            authService = authService,
            secureStorage = secureStorage,
            cookieJar = cookieJar,
            mirrorManager = mirrorManager,
            cookiePersistence = cookiePersistence,
            preferencesRepository = preferencesRepository,
            dispatchers =
                object : AppDispatchers {
                    override val io: CoroutineDispatcher = dispatcher
                    override val default: CoroutineDispatcher = dispatcher
                    override val main: CoroutineDispatcher = dispatcher
                    override val unconfined: CoroutineDispatcher = dispatcher
                },
            loggerFactory = NoOpLoggerFactory,
        )

    private fun sessionCookie(): Cookie =
        Cookie
            .Builder()
            .name("bb_session")
            .value("session")
            .domain("mirror.example")
            .build()
}
