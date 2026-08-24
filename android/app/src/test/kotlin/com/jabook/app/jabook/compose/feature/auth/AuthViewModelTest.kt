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

package com.jabook.app.jabook.compose.feature.auth

import app.cash.turbine.test
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.CaptchaData
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.repository.CaptchaRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val authRepository: AuthRepository = mock()
    private val mirrorManager: MirrorManager = mock()
    private val context: android.content.Context = mock()
    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val authStatusFlow = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepository.authStatus).thenReturn(authStatusFlow)
        whenever(context.getString(com.jabook.app.jabook.R.string.captchaRequired)).thenReturn("Captcha required")
        whenever(context.getString(com.jabook.app.jabook.R.string.unknown_error)).thenReturn("Unknown error")
        viewModel = AuthViewModel(context, authRepository, mirrorManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is valid`() =
        runTest {
            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertNull(state.error)
            assertNull(state.captchaData)
            assertNull(state.savedUsername)
        }

    @Test
    fun `loadSavedCredentials exposes username without password`() =
        runTest(testDispatcher.scheduler) {
            val credentials = UserCredentials("user", "pass")
            whenever(authRepository.getStoredCredentials()).thenReturn(credentials)

            // Re-init viewmodel to trigger init block or call private if exposed.
            // Ideally we should test public methods, but init block runs on creation.
            // Since loadSavedCredentials is async in init, we need to advance dispatcher.

            // Let's create a new VM instance for this test to correctly capture init behavior
            viewModel = AuthViewModel(context, authRepository, mirrorManager)
            advanceUntilIdle()

            assertEquals(credentials.username, viewModel.uiState.value.savedUsername)
        }

    @Test
    fun `login success updates state`() =
        runTest(testDispatcher.scheduler) {
            val username = "testuser"
            val password = "password"
            whenever(authRepository.login(any())).thenReturn(Result.success(true))

            viewModel.login(username, password, rememberMe = true)

            // Loading state check might require manual advance if standard dispatcher used
            // but here we just check final result after idle
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)
            verify(authRepository).saveCredentials(UserCredentials(username, password))
        }

    @Test
    fun `login failure with error updates state`() =
        runTest(testDispatcher.scheduler) {
            val errorMsg = "Login failed"
            whenever(authRepository.login(any())).thenReturn(Result.failure(Exception(errorMsg)))

            viewModel.login("user", "pass", rememberMe = false)
            advanceUntilIdle()

            assertEquals(errorMsg, viewModel.uiState.value.error)
            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals(true, viewModel.uiState.value.showWebViewLogin)
            verify(authRepository, never()).saveCredentials(any())
        }

    @Test
    fun `webview login request is consumed after navigation`() {
        viewModel.requestWebViewLogin()
        assertEquals(true, viewModel.uiState.value.showWebViewLogin)
        viewModel.consumeWebViewLoginRequest()

        assertEquals(false, viewModel.uiState.value.showWebViewLogin)
    }

    @Test
    fun `webview fallback switches an untrusted mirror to a canonical origin`() =
        runTest {
            val canonicalMirror = MirrorManager.DEFAULT_MIRRORS.first()
            whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow("custom.example"))
            whenever(mirrorManager.getBaseUrl()).thenReturn("https://$canonicalMirror")

            assertEquals("https://$canonicalMirror/forum/login.php", viewModel.prepareWebViewLogin())
            verify(mirrorManager).setMirror(canonicalMirror)
        }

    @Test
    fun `webview fallback keeps a default mirror`() =
        runTest {
            val defaultMirror = MirrorManager.DEFAULT_MIRRORS.first()
            whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow(defaultMirror))
            whenever(mirrorManager.getBaseUrl()).thenReturn("https://$defaultMirror")

            assertEquals("https://$defaultMirror/forum/login.php", viewModel.prepareWebViewLogin())
            verify(mirrorManager, never()).setMirror(any())
        }

    @Test
    fun `login failure with captcha updates state`() =
        runTest(testDispatcher.scheduler) {
            val captchaData = CaptchaData("http://url", "test-sid")
            whenever(authRepository.login(any())).thenReturn(Result.failure(CaptchaRequiredException(captchaData)))

            viewModel.login("user", "pass", rememberMe = true)
            advanceUntilIdle()

            assertEquals("Captcha required", viewModel.uiState.value.error)
            assertEquals(captchaData, viewModel.uiState.value.captchaData)
        }

    @Test
    fun `logout calls repository`() =
        runTest(testDispatcher.scheduler) {
            viewModel.logout()
            advanceUntilIdle()
            verify(authRepository).logout()
        }

    @Test
    fun `login emits loading then completion states with Turbine`() =
        runTest(testDispatcher.scheduler) {
            whenever(authRepository.login(any())).thenReturn(Result.success(true))

            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(false, initial.isLoading)
                assertNull(initial.error)

                viewModel.login("test-user", "test-pass", rememberMe = false)
                runCurrent()

                val loading = awaitItem()
                assertEquals(true, loading.isLoading)
                assertNull(loading.error)

                advanceUntilIdle()

                val completed = awaitItem()
                assertEquals(false, completed.isLoading)
                assertNull(completed.error)
                verify(authRepository).clearStoredCredentials()

                cancelAndIgnoreRemainingEvents()
            }
        }
}
