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

package com.jabook.app.jabook.compose.feature.webview

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.jabook.app.jabook.compose.navigation.WebViewRoute
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * WebView screen for displaying web content.
 *
 * Features:
 * - Full WebView with JavaScript support
 * - Cookie management
 * - Magnet link handling
 * - Back button handling for WebView navigation
 * - Loading progress indicator
 * - Page title display
 *
 * @param route Navigation route containing URL
 * @param onNavigateBack Callback to navigate back
 * @param onMagnetLinkDetected Callback when a magnet link is detected
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    route: WebViewRoute,
    onNavigateBack: () -> Unit,
    onMagnetLinkDetected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Decode URL from navigation argument
    val url =
        remember(route.url) {
            URLDecoder.decode(route.url, StandardCharsets.UTF_8.toString())
        }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }

    // Handle back button - navigate in WebView if possible
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle.ifEmpty { "Loading..." },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (canGoBack) {
                                webView?.goBack()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector =
                                    if (canGoBack) {
                                        Icons.AutoMirrored.Filled.ArrowBack
                                    } else {
                                        Icons.Filled.Close
                                    },
                                contentDescription = "Back",
                            )
                        }
                    },
                )

                // Loading progress bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    isLoading = true
                                    canGoBack = view?.canGoBack() ?: false

                                    // Check for magnet links
                                    if (url?.startsWith("magnet:") == true) {
                                        onMagnetLinkDetected?.invoke(url)
                                        // Prevent WebView from loading magnet link
                                        view?.stopLoading()
                                    }
                                }

                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    isLoading = false
                                    loadingProgress = 1f
                                    pageTitle = view?.title ?: ""
                                    canGoBack = view?.canGoBack() ?: false
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val requestUrl = request?.url?.toString()

                                    // Handle magnet links
                                    if (requestUrl?.startsWith("magnet:") == true) {
                                        onMagnetLinkDetected?.invoke(requestUrl)
                                        return true // Don't load in WebView
                                    }

                                    return false // Let WebView handle other URLs
                                }
                            }

                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onProgressChanged(
                                    view: WebView?,
                                    newProgress: Int,
                                ) {
                                    loadingProgress = newProgress / 100f
                                }

                                override fun onReceivedTitle(
                                    view: WebView?,
                                    title: String?,
                                ) {
                                    pageTitle = title ?: ""
                                }
                            }

                        // WebView settings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }

                        // Accept third-party cookies for better compatibility
                        android.webkit.CookieManager
                            .getInstance()
                            .setAcceptThirdPartyCookies(this, true)

                        // Load the URL
                        loadUrl(url)

                        webView = this
                    }
                },
                update = { view ->
                    // Update WebView if needed
                    webView = view
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
