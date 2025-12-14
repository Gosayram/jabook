package com.jabook.app.jabook.compose.feature.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebViewViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        /**
         * Called when a page finishes loading in WebView.
         * Syncs cookies from WebView to Native storage.
         */
        fun onPageFinished(url: String?) {
            if (url == null) return

            // We only care about syncing if it's a relevant domain,
            // but AuthRepository.syncCookiesFromWebView checks the base URL internally or uses CookieManager for the specific API URL.
            // It's safe to call it.
            viewModelScope.launch {
                authRepository.syncCookiesFromWebView()
            }
        }
    }
