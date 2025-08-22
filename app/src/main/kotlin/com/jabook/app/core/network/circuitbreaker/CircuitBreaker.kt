package com.jabook.app.core.network.circuitbreaker

import com.jabook.app.core.network.exceptions.RuTrackerException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Circuit Breaker implementation for RuTracker domains
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Circuit is open, requests fail fast
 * - HALF_OPEN: Testing if service has recovered
 */
enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

/**
 * Circuit Breaker configuration
 */
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val recoveryTimeout: Long = 60_000L, // 1 minute
    val expectedException: Class<out Throwable> = RuTrackerException::class.java,
    val halfOpenAttempts: Int = 3,
)

/**
 * Circuit Breaker implementation
 */
class CircuitBreaker(
    private val config: CircuitBreakerConfig = CircuitBreakerConfig(),
) {
    private val mutex = Mutex()
    private var state: CircuitBreakerState = CircuitBreakerState.CLOSED
    private var failureCount = AtomicInteger(0)
    private var lastFailureTime = 0L
    private var halfOpenAttemptCount = 0

    /**
     * Check if request is allowed
     */
    suspend fun allowRequest(): Boolean =
        mutex.withLock {
            when (state) {
                CircuitBreakerState.CLOSED -> true
                CircuitBreakerState.OPEN -> {
                    val now = System.currentTimeMillis()
                    if (now - lastFailureTime >= config.recoveryTimeout) {
                        state = CircuitBreakerState.HALF_OPEN
                        halfOpenAttemptCount = 0
                        true
                    } else {
                        false
                    }
                }
                CircuitBreakerState.HALF_OPEN -> {
                    if (halfOpenAttemptCount >= config.halfOpenAttempts) {
                        state = CircuitBreakerState.OPEN
                        lastFailureTime = System.currentTimeMillis()
                        false
                    } else {
                        true
                    }
                }
            }
        }

    /**
     * Record successful request
     */
    suspend fun recordSuccess() {
        mutex.withLock {
            when (state) {
                CircuitBreakerState.CLOSED -> {
                    failureCount.set(0)
                }
                CircuitBreakerState.HALF_OPEN -> {
                    state = CircuitBreakerState.CLOSED
                    failureCount.set(0)
                    halfOpenAttemptCount = 0
                }
                CircuitBreakerState.OPEN -> {
                    // Should not happen, but handle gracefully
                    state = CircuitBreakerState.CLOSED
                    failureCount.set(0)
                }
            }
        }
    }

    /**
     * Record failed request
     */
    suspend fun recordFailure(exception: Throwable? = null) {
        mutex.withLock {
            val shouldCountFailure =
                exception == null ||
                    config.expectedException.isInstance(exception)

            if (!shouldCountFailure) {
                return
            }

            when (state) {
                CircuitBreakerState.CLOSED -> {
                    val newFailureCount = failureCount.incrementAndGet()
                    if (newFailureCount >= config.failureThreshold) {
                        state = CircuitBreakerState.OPEN
                        lastFailureTime = System.currentTimeMillis()
                    }
                }
                CircuitBreakerState.HALF_OPEN -> {
                    halfOpenAttemptCount++
                    if (halfOpenAttemptCount >= config.halfOpenAttempts) {
                        state = CircuitBreakerState.OPEN
                        lastFailureTime = System.currentTimeMillis()
                    }
                }
                CircuitBreakerState.OPEN -> {
                    // Already open, just update failure time
                    lastFailureTime = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * Get current state
     */
    suspend fun getState(): CircuitBreakerState =
        mutex.withLock {
            // Check if we should transition from OPEN to HALF_OPEN
            if (state == CircuitBreakerState.OPEN) {
                val now = System.currentTimeMillis()
                if (now - lastFailureTime >= config.recoveryTimeout) {
                    state = CircuitBreakerState.HALF_OPEN
                    halfOpenAttemptCount = 0
                }
            }
            state
        }

    /**
     * Get current failure count
     */
    suspend fun getFailureCount(): Int =
        mutex.withLock {
            failureCount.get()
        }

    /**
     * Get time since last failure
     */
    suspend fun getTimeSinceLastFailure(): Long =
        mutex.withLock {
            if (lastFailureTime == 0L) 0 else System.currentTimeMillis() - lastFailureTime
        }

    /**
     * Reset circuit breaker to CLOSED state
     */
    suspend fun reset() {
        mutex.withLock {
            state = CircuitBreakerState.CLOSED
            failureCount.set(0)
            lastFailureTime = 0L
            halfOpenAttemptCount = 0
        }
    }

    /**
     * Force circuit breaker to OPEN state
     */
    suspend fun forceOpen() {
        mutex.withLock {
            state = CircuitBreakerState.OPEN
            lastFailureTime = System.currentTimeMillis()
        }
    }

    /**
     * Get circuit breaker status as string
     */
    suspend fun getStatus(): String =
        mutex.withLock {
            val failureCount = failureCount.get()
            val timeSinceFailure = if (lastFailureTime == 0L) 0 else System.currentTimeMillis() - lastFailureTime

            """
            Circuit Breaker Status:
            State: $state
            Failure Count: $failureCount
            Time Since Last Failure: ${timeSinceFailure}ms
            Half-Open Attempts: $halfOpenAttemptCount
            Config: failureThreshold=${config.failureThreshold}, recoveryTimeout=${config.recoveryTimeout}ms
            """.trimIndent()
        }
}

/**
 * Circuit Breaker factory for creating configured instances
 */
object CircuitBreakerFactory {
    fun create(
        failureThreshold: Int = 5,
        recoveryTimeout: Long = 60_000L,
        expectedException: Class<out Throwable> = RuTrackerException::class.java,
        halfOpenAttempts: Int = 3,
    ): CircuitBreaker =
        CircuitBreaker(
            CircuitBreakerConfig(
                failureThreshold = failureThreshold,
                recoveryTimeout = recoveryTimeout,
                expectedException = expectedException,
                halfOpenAttempts = halfOpenAttempts,
            ),
        )

    fun createForDomain(domain: String): CircuitBreaker {
        // Different configurations for different domains
        return when (domain) {
            "rutracker.me" ->
                create(
                    failureThreshold = 3, // Lower threshold for primary domain
                    recoveryTimeout = 30_000L, // Faster recovery for primary domain
                    halfOpenAttempts = 2,
                )
            "rutracker.org" ->
                create(
                    failureThreshold = 5,
                    recoveryTimeout = 60_000L,
                    halfOpenAttempts = 3,
                )
            "rutracker.net" ->
                create(
                    failureThreshold = 2, // Very low threshold for broken domain
                    recoveryTimeout = 300_000L, // Long recovery time
                    halfOpenAttempts = 1,
                )
            else -> create() // Default configuration
        }
    }
}
