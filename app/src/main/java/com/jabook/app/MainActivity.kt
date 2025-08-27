package com.jabook.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.jabook.core.net.interceptor.UaInterceptor
import com.jabook.core.net.repository.UserAgentRepository
import com.jabook.ui.theme.JaBookTheme
import com.jabook.app.WebViewAssetLoaderHelper

/**
 * Main Activity for JaBook app
 * Hosts the WebView that loads the Svelte SPA
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var webView: WebView
    private lateinit var userAgentRepository: UserAgentRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize WebViewAssetLoaderHelper
        WebViewAssetLoaderHelper.initialize(this)
        
        userAgentRepository = UserAgentRepository(this)
        
        setContent {
            JaBookTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebViewContainer(
                        modifier = Modifier.padding(innerPadding),
                        userAgentRepository = userAgentRepository
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update User-Agent if needed when WebView is resumed
        if (::webView.isInitialized) {
            userAgentRepository.updateIfNeeded(webView)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::webView.isInitialized) {
            webView.parent?.let { (it as? View)?.parent as? ViewGroup?.removeView(webView) }
            webView.destroy()
        }
    }
    
    /**
     * WebView container Composable
     */
    @Composable
    private fun WebViewContainer(
        modifier: Modifier = Modifier,
        userAgentRepository: UserAgentRepository
    ) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    setupWebView(userAgentRepository)
                }
            },
            update = { webView ->
                // WebView updates can be handled here if needed
            }
        )
    }
    
    /**
     * Sets up the WebView with proper configuration
     */
    private fun WebView.setupWebView(userAgentRepository: UserAgentRepository) {
        this@MainActivity.webView = this
        
        // Setup WebViewAssetLoader for local assets
        val assetLoader = WebViewAssetLoaderHelper.getAssetLoader()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return assetLoader.shouldOverrideUrlLoading(request)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject JavaScript bridge for communication between WebView and native code
                injectJavaScriptInterface()
            }
        }
        
        // Enable JavaScript
        settings.javaScriptEnabled = true
        
        // Enable media playback without user gesture (for audio)
        settings.mediaPlaybackRequiresUserGesture = false
        
        // Enable DOM storage
        settings.domStorageEnabled = true
        
        // Enable cache
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // Enable third-party cookies if needed
        settings.setAllowFileAccessFromFileURLs(true)
        settings.setAllowUniversalAccessFromFileURLs(true)
        
        // Set User-Agent
        userAgentRepository.updateIfNeeded(this)
        settings.userAgentString = userAgentRepository.get()
        
        // Enable hardware acceleration
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        settings.setEnableSmoothTransition(true)
        
        // Set Chrome client for handling JavaScript dialogs, etc.
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // Handle loading progress
            }
        }
        
        // Load the Svelte SPA from assets
        loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }
    
    /**
     * Injects JavaScript interface for WebView-Svelte communication
     */
    private fun WebView.injectJavaScriptInterface() {
        // Create JavaScript bridge object
        val bridge = """
            (function() {
                window.JaBook = {
                    api: {
                        ping: function() {
                            return fetch('/api/ping')
                                .then(response => response.json())
                                .then(data => ({ success: true, data }))
                                .catch(error => ({ success: false, error: error.message }));
                        },
                        getEndpoints: function() {
                            return fetch('/api/endpoints')
                                .then(response => response.json())
                                .then(data => ({ success: true, data }))
                                .catch(error => ({ success: false, error: error.message }));
                        },
                        addEndpoint: function(url) {
                            return fetch('/api/endpoints', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ url })
                            })
                            .then(response => response.json())
                            .then(data => ({ success: true, data }))
                            .catch(error => ({ success: false, error: error.message }));
                        },
                        getActiveEndpoint: function() {
                            return fetch('/api/endpoints/active')
                                .then(response => response.json())
                                .then(data => ({ success: true, data }))
                                .catch(error => ({ success: false, error: error.message }));
                        }
                    },
                    utils: {
                        getPlatform: function() {
                            return 'android';
                        },
                        getVersion: function() {
                            return '1.0.0';
                        },
                        isOnline: function() {
                            return navigator.onLine;
                        }
                    }
                };
            })();
        """.trimIndent()
        
        evaluateJavascript(bridge, null)
    }
    
    /**
     * Clears all cookies
     */
    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
    
    /**
     * Gets the current User-Agent
     */
    fun getUserAgent(): String {
        return userAgentRepository.get()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JaBookTheme {
        Greeting("Android")
    }
}