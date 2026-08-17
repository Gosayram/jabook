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
        assertTrue(viewModel.isTrustedAuthenticationUrl("https://rutracker.org/forum/login.php"))
        assertTrue(viewModel.isTrustedAuthenticationUrl("https://rutracker.net/"))

        listOf(
            "http://rutracker.org/forum/login.php",
            "https://rutracker.org:8443/forum/login.php",
            "https://user:password@rutracker.org/forum/login.php",
            "https://evilrutracker.org/forum/login.php",
            "https://rutracker.ru/forum/login.php",
            "https://rutracker.info/forum/login.php",
            "file:///android_asset/login.html",
            "javascript:alert(1)",
        ).forEach { url -> assertFalse(url, viewModel.isTrustedAuthenticationUrl(url)) }
    }

    @Test
    fun `malformed percent encoded deep link URL is rejected without throwing`() {
        assertNull(decodeWebViewUrl("https%3A%2F%2Frutracker.org%2F%"))
    }

    @Test
    fun `complete login syncs then reports validated session`() =
        runTest(testDispatcher.scheduler) {
            val authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Authenticated("test"))
            whenever(authRepository.authStatus).thenReturn(authStatus)
            var result: Boolean? = null

            viewModel.completeLogin("https://rutracker.org/forum/") { result = it }
            advanceUntilIdle()

            verify(authRepository).syncCookiesFromWebView("https://rutracker.org/forum/")
            assertTrue(result == true)
        }

    @Test
    fun `complete login reports failure when session not authenticated`() =
        runTest(testDispatcher.scheduler) {
            val authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)
            whenever(authRepository.authStatus).thenReturn(authStatus)
            var result: Boolean? = null

            viewModel.completeLogin("https://rutracker.org/forum/") { result = it }
            advanceUntilIdle()

            verify(authRepository).syncCookiesFromWebView("https://rutracker.org/forum/")
            assertFalse(result == true)
        }
}
