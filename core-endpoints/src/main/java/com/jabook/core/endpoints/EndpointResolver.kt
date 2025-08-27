package com.jabook.core.endpoints

import android.content.Context
import android.util.Log
import com.jabook.core.net.Http
import com.jabook.core.net.interceptor.UaInterceptor
import com.jabook.core.net.repository.UserAgentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Endpoint Resolver for RuTracker mirrors
 * Manages mirror list, health checks, and failover functionality
 */
class EndpointResolver(
    private val context: Context,
    private val userAgentRepository: UserAgentRepository
) {
    
    private val TAG = "EndpointResolver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Default RuTracker mirrors
    private val defaultMirrors = listOf(
        "https://rutracker.org",
        "https://rutracker.net",
        "https://rutracker.online",
        "https://rutracker.party"
    )
    
    // Mirror storage
    private val mirrors = mutableListOf<Mirror>()
    private val activeMirror = mutableStateOf<Mirror?>(null)
    private val healthCheckMutex = Mutex()
    
    // HTTP client for health checks
    private val httpClient = Http.createClient(
        context = context,
        userAgentInterceptor = UaInterceptor(userAgentRepository),
        enableLogging = false
    )
    
    // Mirror data class
    data class Mirror(
        val url: String,
        var healthy: Boolean = false,
        var status: String = "unchecked",
        var responseTime: Long = 0L,
        var lastChecked: Long = 0L,
        var error: String? = null
    )
    
    // Initialize with default mirrors
    init {
        loadMirrors()
        startPeriodicHealthCheck()
    }
    
    /**
     * Loads mirrors from storage or uses defaults
     */
    private fun loadMirrors() {
        // For now, use default mirrors
        // In production, this would load from SharedPreferences or database
        mirrors.clear()
        defaultMirrors.forEach { url ->
            mirrors.add(Mirror(url))
        }
        
        // Set first mirror as active
        if (mirrors.isNotEmpty()) {
            activeMirror.value = mirrors[0]
        }
        
        Log.i(TAG, "Loaded ${mirrors.size} mirrors")
    }
    
    /**
     * Gets all mirrors
     */
    fun getAllMirrors(): List<Mirror> {
        return mirrors.toList()
    }
    
    /**
     * Gets the currently active mirror
     */
    fun getActiveMirror(): Mirror? {
        return activeMirror.value
    }
    
    /**
     * Sets a mirror as active
     */
    fun setActiveMirror(mirror: Mirror) {
        activeMirror.value = mirror
        Log.i(TAG, "Set active mirror: ${mirror.url}")
    }
    
    /**
     * Adds a new mirror
     */
    suspend fun addMirror(url: String): Result<Mirror> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate URL
                if (!url.matches(Regex("^https?://[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#[\\]@!$&'()*+,;=]*$"))) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid URL format"))
                }
                
                // Check if mirror already exists
                if (mirrors.any { it.url.equals(url, ignoreCase = true) }) {
                    return@withContext Result.failure(IllegalArgumentException("Mirror already exists"))
                }
                
                // Create new mirror and check health
                val mirror = Mirror(url)
                val healthResult = checkMirrorHealth(mirror)
                
                if (healthResult.isSuccess) {
                    mirrors.add(mirror)
                    Log.i(TAG, "Added new mirror: $url")
                    Result.success(mirror)
                } else {
                    Result.failure(healthResult.exceptionOrNull() ?: IllegalStateException("Health check failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add mirror: $url", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Removes a mirror
     */
    fun removeMirror(mirror: Mirror) {
        mirrors.remove(mirror)
        if (activeMirror.value == mirror) {
            // Set next available mirror as active
            activeMirror.value = mirrors.firstOrNull()
        }
        Log.i(TAG, "Removed mirror: ${mirror.url}")
    }
    
    /**
     * Resets to default mirrors
     */
    fun resetToDefaults() {
        mirrors.clear()
        defaultMirrors.forEach { url ->
            mirrors.add(Mirror(url))
        }
        
        // Set first mirror as active
        if (mirrors.isNotEmpty()) {
            activeMirror.value = mirrors[0]
        }
        
        Log.i(TAG, "Reset to ${mirrors.size} default mirrors")
    }
    
    /**
     * Rechecks health of all mirrors
     */
    suspend fun recheckAllMirrors(): Result<List<Mirror>> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<Mirror>()
                
                mirrors.forEach { mirror ->
                    val healthResult = checkMirrorHealth(mirror)
                    if (healthResult.isSuccess) {
                        results.add(mirror)
                    }
                }
                
                // Update active mirror if needed
                updateActiveMirror()
                
                Log.i(TAG, "Health check completed. ${results.size} healthy mirrors out of ${mirrors.size}")
                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recheck mirrors", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Checks health of a single mirror
     */
    private suspend fun checkMirrorHealth(mirror: Mirror): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // Create request to mirror's index page
                val request = Request.Builder()
                    .url("${mirror.url}/index.php")
                    .head() // Use HEAD request for faster health check
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseTime = System.currentTimeMillis() - startTime
                
                // Check if response is healthy
                val isHealthy = response.isSuccessful && 
                               response.code in 200..399 &&
                               response.header("Content-Type")?.contains("text/html") == true
                
                // Update mirror status
                mirror.healthy = isHealthy
                mirror.responseTime = responseTime
                mirror.lastChecked = System.currentTimeMillis()
                mirror.status = if (isHealthy) "healthy" else else "unhealthy"
                mirror.error = if (!isHealthy) "HTTP ${response.code}" else null
                
                response.close()
                
                if (isHealthy) {
                    Log.d(TAG, "Mirror healthy: ${mirror.url} (${responseTime}ms)")
                    Result.success(Unit)
                } else {
                    Log.w(TAG, "Mirror unhealthy: ${mirror.url} (${response.code})")
                    Result.failure(IllegalStateException("Mirror returned ${response.code}"))
                }
            } catch (e: Exception) {
                mirror.healthy = false
                mirror.responseTime = 0
                mirror.lastChecked = System.currentTimeMillis()
                mirror.status = "unhealthy"
                mirror.error = e.message
                
                Log.w(TAG, "Health check failed for ${mirror.url}: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    /**
     * Updates the active mirror based on health status
     */
    private suspend fun updateActiveMirror() {
        healthCheckMutex.withLock {
            // Find the best healthy mirror (lowest response time)
            val healthyMirrors = mirrors.filter { it.healthy }
            
            if (healthyMirrors.isNotEmpty()) {
                val bestMirror = healthyMirrors.minByOrNull { it.responseTime } ?: healthyMirrors[0]
                activeMirror.value = bestMirror
                Log.i(TAG, "Updated active mirror: ${bestMirror.url} (${bestMirror.responseTime}ms)")
            } else {
                // No healthy mirrors, use first available
                activeMirror.value = mirrors.firstOrNull()
                Log.w(TAG, "No healthy mirrors available")
            }
        }
    }
    
    /**
     * Starts periodic health checks
     */
    private fun startPeriodicHealthCheck() {
        scope.launch {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(5)) // Check every 5 minutes
                
                try {
                    recheckAllMirrors()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic health check failed", e)
                }
            }
        }
    }
    
    /**
     * Gets the current active URL
     */
    fun getActiveUrl(): String? {
        return activeMirror.value?.url
    }
    
    /**
     * Checks if we have any healthy mirrors
     */
    fun hasHealthyMirrors(): Boolean {
        return mirrors.any { it.healthy }
    }
    
    /**
     * Gets statistics about mirrors
     */
    fun getMirrorStats(): Map<String, Any> {
        val total = mirrors.size
        val healthy = mirrors.count { it.healthy }
        val unhealthy = total - healthy
        
        return mapOf(
            "total" to total,
            "healthy" to healthy,
            "unhealthy" to unhealthy,
            "active" to activeMirror.value?.url,
            "lastCheck" to mirrors.maxOfOrNull { it.lastChecked } ?: 0L
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        Log.i(TAG, "EndpointResolver cleaned up")
    }
}