package com.jabook.app

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPath
import androidx.webkit.WebViewAssetLoader.ResourcesPath
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Helper class for configuring WebViewAssetLoader
 * Handles loading Svelte SPA from assets with proper MIME types
 */
object WebViewAssetLoaderHelper {
    
    /**
     * Creates and configures WebViewAssetLoader
     */
    fun createAssetLoader(context: Context): WebViewAssetLoader {
        return WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPath(context.assets))
            .addPathHandler("/res/", ResourcesPath(context.resources))
            .build()
    }
    
    /**
     * Custom WebViewClient that handles asset loading
     */
    class AssetWebViewClient(private val assetLoader: WebViewAssetLoader) : WebViewClient() {
        
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.url)
        }
        
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            
            // Handle local asset URLs
            if (url.startsWith("https://appassets.androidplatform.net/")) {
                return false // Let WebView handle local assets
            }
            
            // Handle external URLs if needed
            // For now, let WebView handle all URLs
            return false
        }
        
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            // Inject JavaScript if needed
            injectCustomJavaScript(view)
        }
        
        /**
         * Injects custom JavaScript into the WebView
         */
        private fun injectCustomJavaScript(webView: WebView) {
            val js = """
                // Global JavaScript object for communication with native code
                window.JaBook = {
                    // API endpoints
                    api: {
                        baseUrl: 'http://127.0.0.1:17171',
                        
                        // Generic API call function
                        call: async function(endpoint, method = 'GET', data = null) {
                            try {
                                const url = this.api.baseUrl + endpoint;
                                const options = {
                                    method: method,
                                    headers: {
                                        'Content-Type': 'application/json'
                                    }
                                };
                                
                                if (data && (method === 'POST' || method === 'PUT')) {
                                    options.body = JSON.stringify(data);
                                }
                                
                                const response = await fetch(url, options);
                                const result = await response.json();
                                
                                if (!response.ok) {
                                    throw new Error(result.message || 'API request failed');
                                }
                                
                                return result;
                            } catch (error) {
                                console.error('API call failed:', error);
                                throw error;
                            }
                        },
                        
                        // Ping endpoint
                        ping: () => this.api.call('/api/ping'),
                        
                        // Get user info
                        me: () => this.api.call('/api/me'),
                        
                        // Search (requires auth)
                        search: (query, page = 1) => this.api.call(`/api/search?q=${'$'}{encodeURIComponent(query)}&page=${'$'}{page}`),
                        
                        // Get topic details
                        topic: (id) => this.api.call(`/api/topic/${'$'}{id}`),
                        
                        // Add torrent
                        addTorrent: (magnetOrUrl) => this.api.call('/api/torrents', 'POST', { magnet: magnetOrUrl }),
                        
                        // Get torrent status
                        torrentStatus: (id) => this.api.call(`/api/torrents/${'$'}{id}`),
                        
                        // Remove torrent
                        removeTorrent: (id) => this.api.call(`/api/torrents/${'$'}{id}`, 'DELETE'),
                        
                        // Get stream URL
                        stream: (id) => `${'$'}{this.api.baseUrl}/stream/${'$'}{id}`
                    },
                    
                    // Utility functions
                    utils: {
                        // Format file size
                        formatFileSize: (bytes) => {
                            if (bytes === 0) return '0 B';
                            const k = 1024;
                            const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
                            const i = Math.floor(Math.log(bytes) / Math.log(k));
                            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
                        },
                        
                        // Format duration
                        formatDuration: (seconds) => {
                            const hours = Math.floor(seconds / 3600);
                            const minutes = Math.floor((seconds % 3600) / 60);
                            const secs = seconds % 60;
                            
                            if (hours > 0) {
                                return `${'$'}{hours}:${'$'}{minutes.toString().padStart(2, '0')}:${'$'}{secs.toString().padStart(2, '0')}`;
                            }
                            return `${'$'}{minutes}:${'$'}{secs.toString().padStart(2, '0')}`;
                        },
                        
                        // Debounce function
                        debounce: (func, wait) => {
                            let timeout;
                            return function executedFunction(...args) {
                                const later = () => {
                                    clearTimeout(timeout);
                                    func(...args);
                                };
                                clearTimeout(timeout);
                                timeout = setTimeout(later, wait);
                            };
                        }
                    }
                };
                
                // Log initialization
                console.log('JaBook API initialized');
            """.trimIndent()
            
            webView.evaluateJavascript(js, null)
        }
    }
}