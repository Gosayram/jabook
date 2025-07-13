package com.jabook.app.shared.performance

import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Performance profiler for monitoring app performance Tracks memory usage, recomposition counts, and performance metrics */
@Singleton
class PerformanceProfiler @Inject constructor(private val debugLogger: IDebugLogger) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isProfilingEnabled = false
    private var recompositionCount = 0L
    private var lastMemoryCheck = 0L

    fun startProfiling() {
        if (isProfilingEnabled) return

        isProfilingEnabled = true
        debugLogger.logInfo("PerformanceProfiler: Starting performance monitoring")

        scope.launch {
            while (isActive && isProfilingEnabled) {
                logPerformanceMetrics()
                delay(5000) // Log every 5 seconds
            }
        }
    }

    fun stopProfiling() {
        isProfilingEnabled = false
        debugLogger.logInfo("PerformanceProfiler: Stopping performance monitoring")
    }

    fun recordRecomposition(componentName: String) {
        recompositionCount++
        if (recompositionCount % 10 == 0L) { // Log every 10 recompositions
            debugLogger.logDebug("PerformanceProfiler: $componentName recomposed $recompositionCount times")
        }
    }

    private fun logPerformanceMetrics() {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
        val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()

        debugLogger.logInfo(
            "PerformanceProfiler: Memory: ${usedMemory}MB/${maxMemory}MB ($memoryUsagePercent%), " + "Recompositions: $recompositionCount"
        )

        // Warn if memory usage is high
        if (memoryUsagePercent > 80) {
            debugLogger.logError("PerformanceProfiler: High memory usage detected: $memoryUsagePercent%", null)
        }

        lastMemoryCheck = usedMemory
    }

    fun measureExecutionTime(operation: String, block: () -> Unit) {
        val startTime = System.currentTimeMillis()
        block()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (duration > 16) { // More than one frame at 60fps
            debugLogger.logDebug("PerformanceProfiler: $operation took ${duration}ms (potential performance issue)")
        }
    }

    suspend fun measureSuspendExecutionTime(operation: String, block: suspend () -> Unit) {
        val startTime = System.currentTimeMillis()
        block()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (duration > 100) { // More than 100ms for suspend operations
            debugLogger.logDebug("PerformanceProfiler: $operation took ${duration}ms")
        }
    }
}
