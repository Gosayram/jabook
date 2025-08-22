package com.jabook.app.core.network.errorhandler

import com.jabook.app.core.network.exceptions.RuTrackerException
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Error severity levels
 */
enum class ErrorSeverity {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL,
}

/**
 * Error context information
 */
data class ErrorContext(
  val operation: String,
  val domain: String? = null,
  val endpoint: String? = null,
  val parameters: Map<String, String> = emptyMap(),
  val timestamp: Long = System.currentTimeMillis(),
  val userId: String? = null,
)

/**
 * Error report with detailed information
 */
data class ErrorReport(
  val id: String,
  val exception: Throwable,
  val severity: ErrorSeverity,
  val context: ErrorContext,
  val userMessage: String,
  val technicalMessage: String,
  val suggestedAction: String,
  val timestamp: Long = System.currentTimeMillis(),
  val isResolved: Boolean = false,
)

/**
 * Error statistics
 */
data class ErrorStatistics(
  val totalErrors: Long,
  val errorsByType: Map<String, Long>,
  val errorsByDomain: Map<String, Long>,
  val errorsBySeverity: Map<ErrorSeverity, Long>,
  val recentErrors: List<ErrorReport>,
  val resolvedErrors: Long,
  val unresolvedErrors: Long,
)

/**
 * Global error handler for RuTracker operations
 */
@Singleton
class RuTrackerErrorHandler
  @Inject
  constructor(
    private val debugLogger: IDebugLogger,
  ) {
    private val errorReports = ConcurrentHashMap<String, ErrorReport>()
    private val errorCounter = AtomicLong(0)
    private val errorCountsByType = ConcurrentHashMap<String, AtomicLong>()
    private val errorCountsByDomain = ConcurrentHashMap<String, AtomicLong>()
    private val errorCountsBySeverity = ConcurrentHashMap<ErrorSeverity, AtomicLong>()

    private val _recentErrors = MutableStateFlow<List<ErrorReport>>(emptyList())
    val recentErrors: Flow<List<ErrorReport>> = _recentErrors.asStateFlow()

    private val _errorStatistics = MutableStateFlow(ErrorStatistics(0, emptyMap(), emptyMap(), emptyMap(), emptyList(), 0, 0))
    val errorStatistics: Flow<ErrorStatistics> = _errorStatistics.asStateFlow()

    companion object {
      private const val MAX_RECENT_ERRORS = 100
      private const val ERROR_ID_PREFIX = "RTE_"
    }

    /**
     * Handle RuTracker exception
     */
    suspend fun handleError(
      exception: Throwable,
      context: ErrorContext,
      severity: ErrorSeverity = determineSeverity(exception),
    ): ErrorReport {
      val errorReport = createErrorReport(exception, context, severity)

      // Store error report
      errorReports[errorReport.id] = errorReport

      // Update counters
      errorCounter.incrementAndGet()
      errorCountsByType.getOrPut(exception::class.simpleName ?: "Unknown") { AtomicLong(0) }.incrementAndGet()
      context.domain?.let { domain ->
        errorCountsByDomain.getOrPut(domain) { AtomicLong(0) }.incrementAndGet()
      }
      errorCountsBySeverity.getOrPut(severity) { AtomicLong(0) }.incrementAndGet()

      // Update recent errors
      updateRecentErrors()

      // Update statistics
      updateErrorStatistics()

      // Log error
      logError(errorReport)

      return errorReport
    }

    /**
     * Determine error severity based on exception type
     */
    private fun determineSeverity(exception: Throwable): ErrorSeverity =
      when (exception) {
        is RuTrackerException.AllDomainsUnavailableException -> ErrorSeverity.CRITICAL
        is RuTrackerException.CircuitBreakerOpenException -> ErrorSeverity.HIGH
        is RuTrackerException.AuthenticationException -> ErrorSeverity.HIGH
        is RuTrackerException.SecurityException -> ErrorSeverity.HIGH
        is RuTrackerException.ServiceUnavailableException -> ErrorSeverity.MEDIUM
        is RuTrackerException.NetworkException -> ErrorSeverity.MEDIUM
        is RuTrackerException.RateLimitException -> ErrorSeverity.MEDIUM
        is RuTrackerException.ParseException -> ErrorSeverity.LOW
        is RuTrackerException.CacheException -> ErrorSeverity.LOW
        is RuTrackerException.OfflineException -> ErrorSeverity.LOW
        else -> ErrorSeverity.MEDIUM
      }

    /**
     * Create error report
     */
    private fun createErrorReport(
      exception: Throwable,
      context: ErrorContext,
      severity: ErrorSeverity,
    ): ErrorReport {
      val errorId = ERROR_ID_PREFIX + System.currentTimeMillis() + "_" + errorCounter.incrementAndGet()

      return ErrorReport(
        id = errorId,
        exception = exception,
        severity = severity,
        context = context,
        userMessage = generateUserMessage(exception),
        technicalMessage = generateTechnicalMessage(exception),
        suggestedAction = generateSuggestedAction(exception),
      )
    }

    /**
     * Generate user-friendly error message
     */
    private fun generateUserMessage(exception: Throwable): String =
      when (exception) {
        is RuTrackerException.AllDomainsUnavailableException ->
          "Все домены RuTracker временно недоступны. Пожалуйста, попробуйте позже."
        is RuTrackerException.CircuitBreakerOpenException ->
          "Сервис временно недоступен из-за технических проблем. Пожалуйста, попробуйте через несколько минут."
        is RuTrackerException.NetworkException ->
          "Проблема с сетевым подключением. Пожалуйста, проверьте ваше интернет-соединение."
        is RuTrackerException.AuthenticationException ->
          "Ошибка аутентификации. Пожалуйста, проверьте ваши учетные данные."
        is RuTrackerException.RateLimitException ->
          "Слишком много запросов. Пожалуйста, подождите немного перед следующей попыткой."
        is RuTrackerException.ServiceUnavailableException ->
          "Сервис RuTracker временно недоступен. Пожалуйста, попробуйте позже."
        is RuTrackerException.ParseException ->
          "Ошибка обработки данных. Пожалуйста, попробуйте еще раз."
        is RuTrackerException.NotFoundException ->
          "Запрашиваемый ресурс не найден."
        is RuTrackerException.CacheException ->
          "Ошибка кэширования данных. Пожалуйста, попробуйте еще раз."
        is RuTrackerException.OfflineException ->
          "Офлайн-режим активен. Некоторые функции могут быть недоступны."
        is RuTrackerException.SearchUnavailableException ->
          "Поиск временно недоступен. Пожалуйста, попробуйте использовать навигацию по категориям."
        else ->
          "Произошла непредвиденная ошибка. Пожалуйста, попробуйте еще раз."
      }

    /**
     * Generate technical error message
     */
    private fun generateTechnicalMessage(exception: Throwable): String =
      buildString {
        append("Error: ${exception::class.simpleName}")
        append(", Message: ${exception.message}")
        exception.cause?.let { cause ->
          append(", Cause: ${cause::class.simpleName}: ${cause.message}")
        }
      }

    /**
     * Generate suggested action for error resolution
     */
    private fun generateSuggestedAction(exception: Throwable): String =
      when (exception) {
        is RuTrackerException.AllDomainsUnavailableException ->
          "Проверьте интернет-соединение и попробуйте позже. Если проблема сохраняется, обратитесь в поддержку."
        is RuTrackerException.CircuitBreakerOpenException ->
          "Подождите несколько минут и попробуйте снова. Если проблема сохраняется, попробуйте переключить домен вручную."
        is RuTrackerException.NetworkException ->
          "Проверьте интернет-соединение, настройки сети и попробуйте снова."
        is RuTrackerException.AuthenticationException ->
          "Проверьте правильность введенных данных. Если проблема сохраняется, сбросьте пароль или обратитесь в поддержку."
        is RuTrackerException.RateLimitException ->
          "Подождите 1-2 минуты перед следующей попыткой. Избегайте слишком частых запросов."
        is RuTrackerException.ServiceUnavailableException ->
          "Попробуйте позже или используйте альтернативный домен, если доступен."
        is RuTrackerException.ParseException ->
          "Попробуйте обновить страницу или повторить запрос. Если проблема сохраняется, очистите кэш приложения."
        is RuTrackerException.NotFoundException ->
          "Проверьте правильность URL или попробуйте поискать другой ресурс."
        is RuTrackerException.CacheException ->
          "Очистите кэш приложения и попробуйте снова."
        is RuTrackerException.OfflineException ->
          "Проверьте интернет-соединение или отключите офлайн-режим в настройках."
        is RuTrackerException.SearchUnavailableException ->
          "Используйте навигацию по категориям вместо поиска или попробуйте позже."
        else ->
          "Перезапустите приложение и попробуйте снова. Если проблема сохраняется, обратитесь в поддержку."
      }

    /**
     * Update recent errors list
     */
    private suspend fun updateRecentErrors() {
      val recent =
        errorReports.values
          .sortedByDescending { it.timestamp }
          .take(MAX_RECENT_ERRORS)
          .toList()

      _recentErrors.value = recent
    }

    /**
     * Update error statistics
     */
    private suspend fun updateErrorStatistics() {
      val stats =
        ErrorStatistics(
          totalErrors = errorCounter.get(),
          errorsByType = errorCountsByType.mapValues { it.value.get() },
          errorsByDomain = errorCountsByDomain.mapValues { it.value.get() },
          errorsBySeverity = errorCountsBySeverity.mapValues { it.value.get() },
          recentErrors = _recentErrors.value,
          resolvedErrors = errorReports.values.count { it.isResolved }.toLong(),
          unresolvedErrors = errorReports.values.count { !it.isResolved }.toLong(),
        )

      _errorStatistics.value = stats
    }

    /**
     * Log error to debug logger
     */
    private fun logError(errorReport: ErrorReport) {
      val logMessage =
        buildString {
          append("RuTracker Error [${errorReport.id}]: ")
          append("Severity=${errorReport.severity}, ")
          append("Operation=${errorReport.context.operation}, ")
          append("Domain=${errorReport.context.domain}, ")
          append("Exception=${errorReport.exception::class.simpleName}, ")
          append("Message=${errorReport.exception.message}")
        }

      when (errorReport.severity) {
        ErrorSeverity.CRITICAL, ErrorSeverity.HIGH ->
          debugLogger.logError(logMessage, errorReport.exception)
        ErrorSeverity.MEDIUM ->
          debugLogger.logWarning(logMessage)
        ErrorSeverity.LOW ->
          debugLogger.logDebug(logMessage)
      }
    }

    /**
     * Mark error as resolved
     */
    suspend fun markErrorAsResolved(errorId: String): Boolean {
      val errorReport = errorReports[errorId]
      return if (errorReport != null && !errorReport.isResolved) {
        errorReports[errorId] = errorReport.copy(isResolved = true)
        updateRecentErrors()
        updateErrorStatistics()
        debugLogger.logInfo("RuTrackerErrorHandler: Error $errorId marked as resolved")
        true
      } else {
        false
      }
    }

    /**
     * Get error report by ID
     */
    suspend fun getErrorReport(errorId: String): ErrorReport? = errorReports[errorId]

    /**
     * Get errors by type
     */
    suspend fun getErrorsByType(exceptionType: String): List<ErrorReport> =
      errorReports.values
        .filter { it.exception::class.simpleName == exceptionType }
        .sortedByDescending { it.timestamp }
        .toList()

    /**
     * Get errors by domain
     */
    suspend fun getErrorsByDomain(domain: String): List<ErrorReport> =
      errorReports.values
        .filter { it.context.domain == domain }
        .sortedByDescending { it.timestamp }
        .toList()

    /**
     * Get errors by severity
     */
    suspend fun getErrorsBySeverity(severity: ErrorSeverity): List<ErrorReport> =
      errorReports.values
        .filter { it.severity == severity }
        .sortedByDescending { it.timestamp }
        .toList()

    /**
     * Clear all errors
     */
    suspend fun clearAllErrors() {
      errorReports.clear()
      errorCounter.set(0)
      errorCountsByType.clear()
      errorCountsByDomain.clear()
      errorCountsBySeverity.clear()

      updateRecentErrors()
      updateErrorStatistics()

      debugLogger.logInfo("RuTrackerErrorHandler: All errors cleared")
    }

    /**
     * Clear resolved errors
     */
    suspend fun clearResolvedErrors() {
      val resolvedIds =
        errorReports.values
          .filter { it.isResolved }
          .map { it.id }
          .toSet()

      resolvedIds.forEach { errorReports.remove(it) }

      updateRecentErrors()
      updateErrorStatistics()

      debugLogger.logInfo("RuTrackerErrorHandler: Cleared ${resolvedIds.size} resolved errors")
    }

    /**
     * Get error summary for UI display
     */
    fun getErrorSummary(): Flow<Map<String, String>> =
      _errorStatistics.map { stats ->
        mapOf(
          "totalErrors" to stats.totalErrors.toString(),
          "unresolvedErrors" to stats.unresolvedErrors.toString(),
          "criticalErrors" to (stats.errorsBySeverity[ErrorSeverity.CRITICAL] ?: 0).toString(),
          "highErrors" to (stats.errorsBySeverity[ErrorSeverity.HIGH] ?: 0).toString(),
          "mediumErrors" to (stats.errorsBySeverity[ErrorSeverity.MEDIUM] ?: 0).toString(),
          "lowErrors" to (stats.errorsBySeverity[ErrorSeverity.LOW] ?: 0).toString(),
        )
      }
  }
