package com.jabook.app.core.network.domain

import com.jabook.app.core.network.circuitbreaker.CircuitBreaker
import com.jabook.app.core.network.circuitbreaker.CircuitBreakerFactory
import com.jabook.app.core.network.exceptions.RuTrackerException
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain health status
 */
data class DomainHealthStatus(
    val domain: String,
    val isAvailable: Boolean,
    val responseTime: Long,
    val lastChecked: Long,
    val failureCount: Int,
    val circuitBreakerState: String,
    val consecutiveFailures: Int
)

/**
 * Domain configuration
 */
data class DomainConfig(
    val baseUrl: String,
    val priority: Int,
    val isActive: Boolean = true,
    val healthCheckPath: String = "/forum/tracker.php",
    val timeout: Long = 10_000L,
    val maxRetries: Int = 3
)

/**
 * RuTracker Domain Manager with health monitoring and automatic failover
 */
@Singleton
class RuTrackerDomainManager @Inject constructor(
    private val httpClient: OkHttpClient,
    private val debugLogger: IDebugLogger,
) {
    companion object {
        private const val HEALTH_CHECK_INTERVAL = 30_000L // 30 seconds
        private const val DOMAIN_SWITCH_COOLDOWN = 5_000L // 5 seconds
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }

    private val mutex = Mutex()
    private val domains = ConcurrentHashMap<String, DomainConfig>()
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val healthStatus = ConcurrentHashMap<String, DomainHealthStatus>()
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastSwitchTime = AtomicLong(0)
    
    private val _currentDomain = MutableStateFlow("rutracker.me")
    val currentDomain: Flow<String> = _currentDomain.asStateFlow()
    
    private val _domainStatuses = MutableStateFlow<Map<String, DomainHealthStatus>>(emptyMap())
    val domainStatuses: Flow<Map<String, DomainHealthStatus>> = _domainStatuses.asStateFlow()
    
    private val isHealthCheckRunning = AtomicBoolean(false)
    private var healthCheckJob: kotlinx.coroutines.Job? = null

    init {
        initializeDomains()
        startHealthMonitoring()
    }

    /**
     * Initialize domains with default configuration
     */
    private fun initializeDomains() {
        val defaultDomains = listOf(
            DomainConfig(
                baseUrl = "https://rutracker.me",
                priority = 1,
                isActive = true,
                healthCheckPath = "/forum/tracker.php",
                timeout = 10_000L,
                maxRetries = 3
            ),
            DomainConfig(
                baseUrl = "https://rutracker.org",
                priority = 2,
                isActive = true,
                healthCheckPath = "/forum/tracker.php",
                timeout = 15_000L,
                maxRetries = 2
            ),
            DomainConfig(
                baseUrl = "https://rutracker.net",
                priority = 3,
                isActive = false, // Disabled by default due to SSL issues
                healthCheckPath = "/forum/tracker.php",
                timeout = 20_000L,
                maxRetries = 1
            )
        )

        defaultDomains.forEach { config ->
            val domain = config.baseUrl.removePrefix("https://").removePrefix("http://")
            domains[domain] = config
            circuitBreakers[domain] = CircuitBreakerFactory.createForDomain(domain)
            failureCounts[domain] = AtomicInteger(0)
            
            // Initialize health status
            healthStatus[domain] = DomainHealthStatus(
                domain = domain,
                isAvailable = false,
                responseTime = 0,
                lastChecked = 0,
                failureCount = 0,
                circuitBreakerState = "CLOSED",
                consecutiveFailures = 0
            )
        }

        debugLogger.logInfo("RuTrackerDomainManager: Initialized ${domains.size} domains")
    }

    /**
     * Start health monitoring for all domains
     */
    private fun startHealthMonitoring() {
        if (isHealthCheckRunning.getAndSet(true)) {
            return
        }

        healthCheckJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            while (isHealthCheckRunning.get()) {
                try {
                    checkAllDomainsHealth()
                    updateDomainStatuses()
                    delay(HEALTH_CHECK_INTERVAL)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerDomainManager: Error in health monitoring", e)
                    delay(HEALTH_CHECK_INTERVAL)
                }
            }
        }

        debugLogger.logInfo("RuTrackerDomainManager: Health monitoring started")
    }

    /**
     * Stop health monitoring
     */
    fun stopHealthMonitoring() {
        isHealthCheckRunning.set(false)
        healthCheckJob?.cancel()
        healthCheckJob = null
        debugLogger.logInfo("RuTrackerDomainManager: Health monitoring stopped")
    }

    /**
     * Check health for all domains
     */
    private suspend fun checkAllDomainsHealth() {
        val checkJobs = domains.map { (domain, config) ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                checkDomainHealth(domain, config)
            }
        }

        checkJobs.awaitAll()
    }

    /**
     * Check health for a specific domain
     */
    private suspend fun checkDomainHealth(domain: String, config: DomainConfig) {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var isAvailable = false
            var responseTime = 0L
            var error: Exception? = null

            try {
                val circuitBreaker = circuitBreakers[domain] ?: return@withContext
                
                // Check if circuit breaker allows requests
                if (!circuitBreaker.allowRequest()) {
                    debugLogger.logWarning("RuTrackerDomainManager: Circuit breaker open for $domain")
                    updateHealthStatus(
                        domain = domain,
                        isAvailable = false,
                        responseTime = 0,
                        error = RuTrackerException.CircuitBreakerOpenException("Circuit breaker open for $domain")
                    )
                    return@withContext
                }

                val url = config.baseUrl + config.healthCheckPath
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val response = httpClient.newCall(request).execute()
                responseTime = System.currentTimeMillis() - startTime
                
                isAvailable = response.isSuccessful && response.code != 502
                
                if (isAvailable) {
                    circuitBreaker.recordSuccess()
                    failureCounts[domain]?.set(0)
                    debugLogger.logDebug("RuTrackerDomainManager: Domain $domain is healthy (response time: ${responseTime}ms)")
                } else {
                    val exception = RuTrackerException.ServiceUnavailableException("Domain $domain returned HTTP ${response.code}")
                    circuitBreaker.recordFailure(exception)
                    handleDomainFailure(domain, exception)
                    debugLogger.logWarning("RuTrackerDomainManager: Domain $domain is unhealthy (HTTP ${response.code})")
                }

            } catch (e: Exception) {
                responseTime = System.currentTimeMillis() - startTime
                error = e
                isAvailable = false
                
                val circuitBreaker = circuitBreakers[domain]
                circuitBreaker?.recordFailure(e)
                handleDomainFailure(domain, e)
                debugLogger.logError("RuTrackerDomainManager: Health check failed for $domain", e)
            }

            updateHealthStatus(
                domain = domain,
                isAvailable = isAvailable,
                responseTime = responseTime,
                error = error
            )
        }
    }

    /**
     * Handle domain failure
     */
    private suspend fun handleDomainFailure(domain: String, error: Exception) {
        val failureCount = failureCounts[domain]?.incrementAndGet() ?: 1
        
        if (failureCount >= MAX_CONSECUTIVE_FAILURES) {
            debugLogger.logWarning("RuTrackerDomainManager: Domain $domain has $failureCount consecutive failures, considering switch")
            attemptDomainSwitch()
        }
    }

    /**
     * Update health status for a domain
     */
    private suspend fun updateHealthStatus(
        domain: String,
        isAvailable: Boolean,
        responseTime: Long,
        error: Exception? = null
    ) {
        mutex.withLock {
            val circuitBreaker = circuitBreakers[domain]
            val failureCount = failureCounts[domain]?.get() ?: 0
            
            healthStatus[domain] = DomainHealthStatus(
                domain = domain,
                isAvailable = isAvailable,
                responseTime = responseTime,
                lastChecked = System.currentTimeMillis(),
                failureCount = failureCount,
                circuitBreakerState = circuitBreaker?.getState()?.name ?: "UNKNOWN",
                consecutiveFailures = if (isAvailable) 0 else failureCount
            )
        }
    }

    /**
     * Update domain statuses flow
     */
    private suspend fun updateDomainStatuses() {
        mutex.withLock {
            _domainStatuses.value = healthStatus.toMap()
        }
    }

    /**
     * Get available domain with automatic failover
     */
    suspend fun getAvailableDomain(): String {
        return mutex.withLock {
            val current = _currentDomain.value
            
            // Check if current domain is still available
            if (isDomainAvailable(current)) {
                return current
            }

            // Try to find next available domain
            attemptDomainSwitch()
            return _currentDomain.value
        }
    }

    /**
     * Check if domain is available
     */
    private suspend fun isDomainAvailable(domain: String): Boolean {
        val status = healthStatus[domain]
        return status?.isAvailable == true && 
               status.circuitBreakerState != "OPEN" &&
               domains[domain]?.isActive == true
    }

    /**
     * Attempt to switch to next available domain
     */
    private suspend fun attemptDomainSwitch() {
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime.get() < DOMAIN_SWITCH_COOLDOWN) {
            return
        }

        val current = _currentDomain.value
        val availableDomains = domains.values
            .filter { it.isActive }
            .sortedBy { it.priority }
            .map { it.baseUrl.removePrefix("https://").removePrefix("http://") }

        for (domain in availableDomains) {
            if (domain != current && isDomainAvailable(domain)) {
                lastSwitchTime.set(now)
                _currentDomain.value = domain
                debugLogger.logInfo("RuTrackerDomainManager: Switched from $current to $domain")
                return
            }
        }

        debugLogger.logWarning("RuTrackerDomainManager: No available domains found, keeping current: $current")
    }

    /**
     * Get current base URL
     */
    suspend fun getCurrentBaseUrl(): String {
        val domain = getAvailableDomain()
        return domains[domain]?.baseUrl ?: "https://rutracker.me"
    }

    /**
     * Get all domains
     */
    fun getAllDomains(): List<String> {
        return domains.keys.toList()
    }

    /**
     * Get domain status
     */
    suspend fun getDomainStatus(domain: String): DomainHealthStatus? {
        return mutex.withLock {
            healthStatus[domain]
        }
    }

    /**
     * Manually set domain
     */
    suspend fun setDomain(domain: String): Boolean {
        return mutex.withLock {
            if (domains.containsKey(domain)) {
                _currentDomain.value = domain
                debugLogger.logInfo("RuTrackerDomainManager: Manually set domain to $domain")
                true
            } else {
                debugLogger.logWarning("RuTrackerDomainManager: Attempted to set unknown domain: $domain")
                false
            }
        }
    }

    /**
     * Enable/disable domain
     */
    suspend fun setDomainActive(domain: String, active: Boolean): Boolean {
        return mutex.withLock {
            val config = domains[domain]
            if (config != null) {
                domains[domain] = config.copy(isActive = active)
                debugLogger.logInfo("RuTrackerDomainManager: Set domain $domain active=$active")
                true
            } else {
                false
            }
        }
    }

    /**
     * Reset circuit breaker for domain
     */
    suspend fun resetCircuitBreaker(domain: String): Boolean {
        return mutex.withLock {
            val circuitBreaker = circuitBreakers[domain]
            if (circuitBreaker != null) {
                circuitBreaker.reset()
                failureCounts[domain]?.set(0)
                debugLogger.logInfo("RuTrackerDomainManager: Reset circuit breaker for $domain")
                true
            } else {
                false
            }
        }
    }

    /**
     * Get circuit breaker status
     */
    suspend fun getCircuitBreakerStatus(domain: String): String? {
        return mutex.withLock {
            circuitBreakers[domain]?.getStatus()
        }
    }

    /**
     * Force health check for specific domain
     */
    suspend fun forceHealthCheck(domain: String): Boolean {
        return mutex.withLock {
            val config = domains[domain]
            if (config != null) {
                checkDomainHealth(domain, config)
                updateDomainStatuses()
                true
            } else {
                false
            }
        }
    }

    /**
     * Get domain statistics
     */
    suspend fun getDomainStatistics(): Map<String, Map<String, Any>> {
        return mutex.withLock {
            domains.mapValues { (domain, config) ->
                val status = healthStatus[domain]
                val circuitBreaker = circuitBreakers[domain]
                val failureCount = failureCounts[domain]?.get() ?: 0
                
                mapOf(
                    "config" to config,
                    "healthStatus" to status,
                    "circuitBreakerState" to (circuitBreaker?.getState()?.name ?: "UNKNOWN"),
                    "failureCount" to failureCount,
                    "isCurrent" to (domain == _currentDomain.value)
                )
            }
        }
    }
}