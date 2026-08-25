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

package com.jabook.app.jabook.compose.feature.webview

import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WebViewViewModelTest {
    private val authRepository: AuthRepository = mock()
    private val mirrorManager: MirrorManager = mock()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: WebViewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WebViewViewModel(authRepository, mirrorManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `only canonical RuTracker HTTPS origins are trusted`() {
        val mirrors = MirrorManager.DEFAULT_MIRRORS
        assertTrue(mirrors.size >= 2)
        assertTrue(viewModel.isTrustedAuthenticationUrl("https://${mirrors[0]}/forum/login.php"))
        assertTrue(viewModel.isTrustedAuthenticationUrl("https://${mirrors[1]}/"))

        listOf(
            "http://${mirrors[0]}/forum/login.php",
            "https://${mirrors[0]}:8443/forum/login.php",
            "https://user:password@${mirrors[0]}/forum/login.php",
            "https://evil${mirrors[0]}/forum/login.php",
            "https://not${mirrors[0]}/forum/login.php",
            "file:///android_asset/login.html",
            "javascript:alert(1)",
        ).forEach { url -> assertFalse(url, viewModel.isTrustedAuthenticationUrl(url)) }
    }

    @Test
    fun `malformed deep link URL is rejected without throwing`() {
        assertNull(sanitizeWebViewUrl("https%3A%2F%2Fmirror.example%2F%"))
        assertNull(sanitizeWebViewUrl("javascript:alert(1)"))
        assertEquals("https://mirror.example/forum/login.php", sanitizeWebViewUrl("https://mirror.example/forum/login.php"))
    }

    @Test
    fun `complete login syncs then reports validated session`() =
        runTest(testDispatcher.scheduler) {
            val authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Authenticated("test"))
            whenever(authRepository.authStatus).thenReturn(authStatus)
            var result: Boolean? = null
            val loginUrl = "https://${MirrorManager.DEFAULT_MIRRORS.first()}/forum/"

            viewModel.completeLogin(loginUrl) { result = it }
            advanceUntilIdle()

            verify(authRepository).syncCookiesFromWebView(loginUrl)
            assertTrue(result == true)
        }

    @Test
    fun `complete login reports failure when session not authenticated`() =
        runTest(testDispatcher.scheduler) {
            val authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)
            whenever(authRepository.authStatus).thenReturn(authStatus)
            var result: Boolean? = null
            val loginUrl = "https://${MirrorManager.DEFAULT_MIRRORS.first()}/forum/"

            viewModel.completeLogin(loginUrl) { result = it }
            advanceUntilIdle()

            verify(authRepository).syncCookiesFromWebView(loginUrl)
            assertFalse(result == true)
        }
}
