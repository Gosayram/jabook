package com.jabook.app

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewAssetLoader

/**
 * Helper class for WebViewAssetLoader configuration
 * Provides asset loading functionality for the Svelte SPA
 */
object WebViewAssetLoaderHelper {
    
    private var context: Context? = null
    
    /**
     * Initializes the helper with a context
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
    
    /**
     * Creates and configures WebViewAssetLoader for local assets
     */
    fun getAssetLoader(): WebViewAssetLoader {
        val appContext = context ?: throw IllegalStateException("WebViewAssetLoaderHelper not initialized. Call initialize() first.")
        
        return WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(appContext))
            .addPathHandler("/js/", AssetsPathHandler(appContext))
            .addPathHandler("/css/", AssetsPathHandler(appContext))
            .addPathHandler("/images/", AssetsPathHandler(appContext))
            .addPathHandler("/fonts/", AssetsPathHandler(appContext))
            .build()
    }
    
    /**
     * WebViewAssetLoader.AssetsPathHandler implementation
     */
    private class AssetsPathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {
        
        override fun handle(path: String): WebResourceResponse? {
            // Remove leading slash and normalize path
            val normalizedPath = path.removePrefix("/")
            
            try {
                val inputStream = context.assets.open(normalizedPath)
                val mimeType = getMimeType(normalizedPath)
                
                return WebResourceResponse(mimeType, "UTF-8", inputStream)
            } catch (e: Exception) {
                // Asset not found, return null to let WebView handle it
                return null
            }
        }
        
        /**
         * Gets the MIME type for a given file path
         */
        private fun getMimeType(path: String): String {
            return when {
                path.endsWith(".html") || path.endsWith(".htm") -> "text/html"
                path.endsWith(".css") -> "text/css"
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".json") -> "application/json"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                path.endsWith(".gif") -> "image/gif"
                path.endsWith(".svg") -> "image/svg+xml"
                path.endsWith(".woff") -> "font/woff"
                path.endsWith(".woff2") -> "font/woff2"
                path.endsWith(".ttf") -> "font/ttf"
                path.endsWith(".mp3") -> "audio/mpeg"
                path.endsWith(".mp4") -> "video/mp4"
                path.endsWith(".webm") -> "video/webm"
                else -> "application/octet-stream"
            }
        }
    }
}