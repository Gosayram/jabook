// Copyright 2025 Jabook Contributors
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.CaptchaData
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.repository.CaptchaRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for AuthScreen.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val captchaData: CaptchaData? = null,
    val showWebViewLogin: Boolean = false,
    val savedCredentials: UserCredentials? = null,
)

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AuthUiState())
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        val authStatus: StateFlow<AuthStatus> =
            authRepository.authStatus.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AuthStatus.Unauthenticated,
            )

        init {
            loadSavedCredentials()
        }

        private fun loadSavedCredentials() {
            viewModelScope.launch {
                val credentials = authRepository.getStoredCredentials()
                _uiState.update { it.copy(savedCredentials = credentials) }
            }
        }

        fun login(
            username: String,
            password: String,
            rememberMe: Boolean,
            captchaCode: String? = null,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val credentials = UserCredentials(username, password)
                val result =
                    if (captchaCode != null && _uiState.value.captchaData != null) {
                        authRepository.loginWithCaptcha(credentials, captchaCode, _uiState.value.captchaData!!)
                    } else {
                        authRepository.login(credentials)
                    }

                result
                    .onSuccess {
                        if (rememberMe) {
                            authRepository.saveCredentials(credentials)
                        } else {
                            authRepository.clearStoredCredentials()
                        }
                        _uiState.update { it.copy(isLoading = false, captchaData = null, error = null) }
                    }.onFailure { e ->
                        if (e is CaptchaRequiredException) {
                            _uiState.update { it.copy(isLoading = false, captchaData = e.captchaData, error = "Captcha required") }
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
                        }
                    }
            }
        }

        fun logout() {
            viewModelScope.launch {
                authRepository.logout()
            }
        }

        fun dismissCaptcha() {
            _uiState.update { it.copy(captchaData = null) }
        }

        fun requestWebViewLogin() {
            _uiState.update { it.copy(showWebViewLogin = true) }
        }

        fun onWebViewLoginCompleted() {
            _uiState.update { it.copy(showWebViewLogin = false) }
            viewModelScope.launch {
                // Sync cookies from WebView and refresh status
                authRepository.syncCookiesFromWebView()
                authRepository.isLoggedIn()
            }
        }
    }
