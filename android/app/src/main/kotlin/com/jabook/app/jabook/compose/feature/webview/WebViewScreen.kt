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

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
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
public fun WebViewScreen(
    route: WebViewRoute,
    onNavigateBack: () -> Unit,
    onMagnetLinkDetected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Decode URL from navigation argument.
    val url = remember(route.url) { decodeWebViewUrl(route.url) }

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateBack = dropUnlessResumed { navigationClickGuard.run(onNavigateBack) }

    LaunchedEffect(url) {
        if (url == null) safeNavigateBack()
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var isCapturingSession by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentWebViewUrl by remember { mutableStateOf("") }

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
                            text = pageTitle.ifEmpty { stringResource(R.string.loading) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (canGoBack) {
                                webView?.goBack()
                            } else {
                                safeNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        if (route.isAuthentication) {
                            TextButton(
                                enabled = !isCapturingSession,
                                onClick = {
                                    isCapturingSession = true
                                    viewModel.completeLogin(currentWebViewUrl) { isLoggedIn ->
                                        isCapturingSession = false
                                        if (isLoggedIn) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    R.string.loginSuccessMessage,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            safeNavigateBack()
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    R.string.webViewLoginFailed,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.done))
                            }
                        }
                        IconButton(onClick = safeNavigateBack) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    },
                )

                // Loading progress bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics {
                                    stateDescription = "Loading web page"
                                },
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
                    .padding(padding)
                    .then(
                        run {
                            val wsc = LocalWindowSizeClass.current
                            val maxW = wsc?.let { AdaptiveUtils.getMaxContentWidth(it) }
                            if (maxW != null) Modifier.widthIn(max = maxW) else Modifier
                        },
                    ),
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
                                    errorMessage = null

                                    // Check for magnet links
                                    if (url?.startsWith("magnet:") == true) {
                                        onMagnetLinkDetected?.invoke(url)
                                        // Prevent WebView from loading magnet link
                                        view?.stopLoading()
                                    } else if (
                                        route.isAuthentication &&
                                        !viewModel.isAllowedDuringAuth(url.orEmpty())
                                    ) {
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
                                    if (url != null) currentWebViewUrl = url
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

                                    if (route.isAuthentication && !viewModel.isAllowedDuringAuth(requestUrl.orEmpty())) {
                                        return true
                                    }

                                    return false // Let WebView handle other URLs
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // Only report main frame errors
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        errorMessage = error?.description?.toString()
                                            ?: "Failed to load page"
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?,
                                ) {
                                    handler?.cancel()
                                    isLoading = false
                                    errorMessage = "SSL error: ${error?.toString() ?: "unknown"}"
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
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                            // databaseEnabled is deprecated
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            // Use system default UA to match OkHttp's WebSettings.getDefaultUserAgent
                            // so Cloudflare cookies are bound to the same fingerprint
                            userAgentString = WebSettings.getDefaultUserAgent(context)
                        }

                        // Authentication stays first-party; third-party cookies are not needed.
                        android.webkit.CookieManager
                            .getInstance()
                            .setAcceptThirdPartyCookies(this, false)

                        // Pre-seed WebView with OkHttp cookies before navigation
                        if (url != null && url.isNotEmpty()) {
                            viewModel.syncCookiesToWebView(url)
                        }

                        // Load the URL
                        if (url == null) {
                            // The navigation effect above closes malformed external deep links.
                        } else if (route.isAuthentication && !viewModel.isTrustedAuthenticationUrl(url)) {
                            safeNavigateBack()
                        } else if (url.isNotEmpty()) {
                            loadUrl(url)
                        } else {
                            // Fallback to login page using current mirror
                            loadUrl(viewModel.getLoginUrl())
                        }

                        webView = this
                    }
                },
                update = { view ->
                    // Update WebView if needed
                    webView = view
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                    if (webView === view) webView = null
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Error overlay
            val currentError = errorMessage
            if (currentError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            text = currentError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                errorMessage = null
                                webView?.reload()
                            },
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

internal fun decodeWebViewUrl(value: String): String? =
    try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
    } catch (_: IllegalArgumentException) {
        null
    }
