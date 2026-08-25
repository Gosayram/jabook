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

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.CaptchaData
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.repository.CaptchaRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
@Immutable
public data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val captchaData: CaptchaData? = null,
    val showWebViewLogin: Boolean = false,
    val savedUsername: String? = null,
)

@HiltViewModel
public class AuthViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val authRepository: AuthRepository,
        private val mirrorManager: MirrorManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AuthUiState())
        public val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        public val authStatus: StateFlow<AuthStatus> =
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
                val username = authRepository.getStoredCredentials()?.username
                _uiState.update { it.copy(savedUsername = username) }
            }
        }

        public fun login(
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
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    captchaData = e.captchaData,
                                    error = context.getString(R.string.captchaRequired),
                                )
                            }
                        } else {
                            requestWebViewLogin(e.message ?: context.getString(R.string.unknown_error))
                        }
                    }
            }
        }

        public fun logout() {
            viewModelScope.launch {
                authRepository.logout()
            }
        }

        public fun dismissCaptcha() {
            _uiState.update { it.copy(captchaData = null) }
        }

        public fun requestWebViewLogin() {
            requestWebViewLogin(error = null)
        }

        private fun requestWebViewLogin(error: String?) {
            _uiState.update { it.copy(isLoading = false, error = error, showWebViewLogin = true) }
        }

        public fun consumeWebViewLoginRequest() {
            _uiState.update { it.copy(showWebViewLogin = false) }
        }

        /** Clears the one-shot error so a cached entry does not re-show a stale snackbar. */
        public fun consumeError() {
            _uiState.update { it.copy(error = null) }
        }

        /** Uses one trusted origin for the WebView, cookie jar, and API validation. */
        public suspend fun prepareWebViewLogin(): String {
            if (mirrorManager.currentMirror.value !in MirrorManager.DEFAULT_MIRRORS) {
                mirrorManager.setMirror(MirrorManager.DEFAULT_MIRRORS.first())
            }
            return "${mirrorManager.getBaseUrl()}/forum/login.php"
        }
    }
