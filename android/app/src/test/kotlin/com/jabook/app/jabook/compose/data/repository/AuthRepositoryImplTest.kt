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

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import com.jabook.app.jabook.compose.data.auth.CookiePersistenceManager
import com.jabook.app.jabook.compose.data.auth.RutrackerAuthService
import com.jabook.app.jabook.compose.data.auth.SecureCredentialStorage
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.any
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

    @Test
    fun `validated WebView session authenticates without saved credentials`() =
        runTest {
            whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow("rutracker.org"))
            whenever(cookieJar.loadForRequest(any())).thenReturn(listOf(sessionCookie()))
            whenever(authService.validateAuth()).thenReturn(true)
            whenever(secureStorage.getCredentials()).thenReturn(null)

            val repository = repository()

            assertTrue(repository.isLoggedIn())
            assertTrue(repository.authStatus.value is AuthStatus.Authenticated)
            verify(cookieJar, never()).clear()
        }

    @Test
    fun `invalid WebView session is rejected and cleared`() =
        runTest {
            whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow("rutracker.org"))
            whenever(cookieJar.loadForRequest(any())).thenReturn(listOf(sessionCookie()))
            whenever(authService.validateAuth()).thenReturn(false)
            whenever(secureStorage.getCredentials()).thenReturn(null)

            val repository = repository()

            assertFalse(repository.isLoggedIn())
            verify(cookieJar, atLeastOnce()).clear()
        }

    private fun repository() =
        AuthRepositoryImpl(
            authService = authService,
            secureStorage = secureStorage,
            cookieJar = cookieJar,
            mirrorManager = mirrorManager,
            cookiePersistence = cookiePersistence,
            loggerFactory = NoOpLoggerFactory,
        )

    private fun sessionCookie(): Cookie =
        Cookie
            .Builder()
            .name("bb_session")
            .value("session")
            .domain("rutracker.org")
            .build()
}
