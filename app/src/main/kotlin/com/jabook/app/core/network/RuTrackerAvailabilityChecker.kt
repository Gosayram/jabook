package com.jabook.app.core.network

import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker Availability Checker
 *
 * Performs periodic availability checks for RuTracker.net
 * Runs every 5 minutes when the app is active
 */
@Singleton
class RuTrackerAvailabilityChecker @Inject constructor(
    private val ruTrackerApiClient: RuTrackerApiClient,
    private val debugLogger: IDebugLogger,
) {
    companion object {
        private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val INITIAL_DELAY_MS = 30 * 1000L // 30 seconds initial delay
    }

    private var availabilityJob: Job? = null
    private var isActive = false

    /**
     * Start periodic availability checks
     */
    fun startAvailabilityChecks(scope: CoroutineScope) {
        if (isActive) {
            debugLogger.logDebug("RuTrackerAvailabilityChecker: Already active, ignoring start request")
            return
        }

        isActive = true
        availabilityJob = scope.launch {
            debugLogger.logInfo("RuTrackerAvailabilityChecker: Starting periodic availability checks")
            
            // Initial delay before first check
            delay(INITIAL_DELAY_MS)
            
            while (isActive) {
                try {
                    performAvailabilityCheck()
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerAvailabilityChecker: Error during availability check", e)
                }
                
                // Wait for next check
                delay(CHECK_INTERVAL_MS)
            }
        }
        
        debugLogger.logDebug("RuTrackerAvailabilityChecker: Availability checks started")
    }

    /**
     * Stop periodic availability checks
     */
    fun stopAvailabilityChecks() {
        if (!isActive) {
            debugLogger.logDebug("RuTrackerAvailabilityChecker: Already inactive, ignoring stop request")
            return
        }

        isActive = false
        availabilityJob?.cancel()
        availabilityJob = null
        
        debugLogger.logInfo("RuTrackerAvailabilityChecker: Stopped periodic availability checks")
    }

    /**
     * Perform a single availability check
     */
    private suspend fun performAvailabilityCheck() {
        try {
            debugLogger.logDebug("RuTrackerAvailabilityChecker: Performing availability check")
            
            val startTime = System.currentTimeMillis()
            val result = ruTrackerApiClient.checkAvailability()
            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime
            
            when {
                result.isSuccess -> {
                    val isAvailable = result.getOrNull() ?: false
                    if (isAvailable) {
                        debugLogger.logInfo("RuTrackerAvailabilityChecker: RuTracker is available (response time: ${responseTime}ms)")
                    } else {
                        debugLogger.logWarning("RuTrackerAvailabilityChecker: RuTracker is not available (response time: ${responseTime}ms)")
                    }
                }
                result.isFailure -> {
                    val exception = result.exceptionOrNull()
                    debugLogger.logError("RuTrackerAvailabilityChecker: Availability check failed (response time: ${responseTime}ms)", exception)
                }
            }
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerAvailabilityChecker: Unexpected error during availability check", e)
        }
    }

    /**
     * Perform a manual availability check
     */
    suspend fun performManualCheck(): Result<Boolean> {
        return try {
            debugLogger.logDebug("RuTrackerAvailabilityChecker: Performing manual availability check")
            ruTrackerApiClient.checkAvailability()
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerAvailabilityChecker: Manual availability check failed", e)
            Result.failure(e)
        }
    }

    /**
     * Check if availability checks are currently active
     */
    fun isAvailabilityChecksActive(): Boolean = isActive
} 