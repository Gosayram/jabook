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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@HiltViewModel
public class WebViewViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val mirrorManager: MirrorManager,
    ) : ViewModel() {
        /** Only first-party, HTTPS RuTracker pages may participate in login. */
        public fun isTrustedAuthenticationUrl(url: String): Boolean = Companion.isTrustedAuthenticationUrl(url)

        /** Returns true if the URL is allowed to load during authentication mode. */
        public fun isAllowedDuringAuth(url: String): Boolean {
            if (isTrustedAuthenticationUrl(url)) return true
            return isCloudflareChallengeUrl(url)
        }

        private fun isCloudflareChallengeUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (!parsed.isHttps) return false
            val host = parsed.host
            return host.endsWith(".cloudflare.com") ||
                host == "cloudflare.com" ||
                parsed.encodedPath.contains("cf-chl") ||
                parsed.encodedPath.contains("turnstile")
        }

        public companion object {
            public fun isTrustedAuthenticationUrl(url: String): Boolean {
                val parsed = url.toHttpUrlOrNull() ?: return false
                return (
                    parsed.isHttps &&
                        parsed.port == 443 &&
                        parsed.username.isEmpty() &&
                        parsed.password.isEmpty() &&
                        parsed.host in TRUSTED_AUTH_HOSTS
                )
            }

            private val TRUSTED_AUTH_HOSTS = MirrorManager.DEFAULT_MIRRORS.toSet()
        }

        /** Captures and validates the session only after the user explicitly confirms login. */
        public fun completeLogin(onComplete: (Boolean) -> Unit) {
            viewModelScope.launch {
                val isLoggedIn =
                    try {
                        authRepository.syncCookiesFromWebView()
                        authRepository.isLoggedIn()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                onComplete(isLoggedIn)
            }
        }

        /**
         * Get login URL using current mirror (for fallback).
         */
        public fun getLoginUrl(): String {
            val baseUrl = mirrorManager.getBaseUrl()
            return "$baseUrl/forum/login.php"
        }

        /**
         * Pre-seed WebView with existing OkHttp cookies for the given URL.
         * Should be called before loadUrl to avoid empty cookie state.
         */
        public fun syncCookiesToWebView(url: String) {
            viewModelScope.launch {
                try {
                    authRepository.syncCookiesToWebView(url)
                } catch (_: Exception) {
                    // Best-effort; WebView works without pre-seeded cookies
                }
            }
        }
    }
