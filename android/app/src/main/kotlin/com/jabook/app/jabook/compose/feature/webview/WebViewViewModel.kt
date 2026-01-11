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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class WebViewViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val mirrorManager: MirrorManager,
    ) : ViewModel() {
        /**
         * Called when a page finishes loading in WebView.
         * Syncs cookies from WebView to Native storage.
         */
        public fun onPageFinished() {
            if (url == null) return

            // We only care about syncing if it's a relevant domain,
            // but AuthRepository.syncCookiesFromWebView checks the base URL internally or uses CookieManager for the specific API URL.
            // It's safe to call it.
            viewModelScope.launch {
                authRepository.syncCookiesFromWebView()
            }
        }

        /**
         * Get login URL using current mirror (for fallback).
         */
        public fun getLoginUrl(): String {
            val baseUrl = mirrorManager.getBaseUrl()
            return "$baseUrl/forum/login.php"
        }
    }
