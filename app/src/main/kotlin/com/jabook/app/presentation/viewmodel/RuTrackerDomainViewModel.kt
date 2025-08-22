package com.jabook.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.network.domain.DomainHealthStatus
import com.jabook.app.core.network.domain.RuTrackerDomainManager
import com.jabook.app.core.network.errorhandler.ErrorReport
import com.jabook.app.core.network.errorhandler.ErrorSeverity
import com.jabook.app.core.network.errorhandler.RuTrackerErrorHandler
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * UI state for domain management
 */
data class DomainManagementUiState(
    val isLoading: Boolean = false,
    val domainStatuses: Map<String, DomainHealthStatus> = emptyMap(),
    val currentDomain: String = "rutracker.me",
    val errorReports: List<ErrorReport> = emptyList(),
    val errorSummary: Map<String, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long = 0,
    val userMessage: String? = null
)

/**
 * ViewModel for managing RuTracker domains and their status
 */
@HiltViewModel
class RuTrackerDomainViewModel @Inject constructor(
    private val domainManager: RuTrackerDomainManager,
    private val errorHandler: RuTrackerErrorHandler,
    private val debugLogger: IDebugLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DomainManagementUiState())
    val uiState: StateFlow<DomainManagementUiState> = _uiState.asStateFlow()

    init {
        observeDomainStatuses()
        observeCurrentDomain()
        observeErrorReports()
        observeErrorSummary()
    }

    /**
     * Observe domain statuses from domain manager
     */
    private fun observeDomainStatuses() {
        domainManager.domainStatuses
            .onEach { statuses ->
                _uiState.update { current ->
                    current.copy(
                        domainStatuses = statuses,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observe current domain changes
     */
    private fun observeCurrentDomain() {
        domainManager.currentDomain
            .onEach { domain ->
                _uiState.update { current ->
                    current.copy(
                        currentDomain = domain
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observe error reports
     */
    private fun observeErrorReports() {
        errorHandler.recentErrors
            .onEach { errors ->
                _uiState.update { current ->
                    current.copy(
                        errorReports = errors
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observe error summary
     */
    private fun observeErrorSummary() {
        errorHandler.getErrorSummary()
            .onEach { summary ->
                _uiState.update { current ->
                    current.copy(
                        errorSummary = summary
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Refresh domain statuses
     */
    fun refreshDomainStatuses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            try {
                val domains = domainManager.getAllDomains()
                domains.forEach { domain ->
                    domainManager.forceHealthCheck(domain)
                }
                
                _uiState.update { it.copy(
                    isRefreshing = false,
                    userMessage = "Статусы доменов обновлены"
                ) }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isRefreshing = false,
                    userMessage = "Ошибка при обновлении статусов доменов"
                ) }
                
                debugLogger.logError("RuTrackerDomainViewModel: Error refreshing domain statuses", e)
            }
        }
    }

    /**
     * Manually switch domain
     */
    fun switchDomain(domain: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val success = domainManager.setDomain(domain)
                if (success) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        userMessage = "Домен переключен на $domain"
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        userMessage = "Не удалось переключить домен на $domain"
                    ) }
                }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    userMessage = "Ошибка при переключении домена"
                ) }
                
                debugLogger.logError("RuTrackerDomainViewModel: Error switching domain", e)
            }
        }
    }

    /**
     * Enable/disable domain
     */
    fun setDomainActive(domain: String, active: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val success = domainManager.setDomainActive(domain, active)
                if (success) {
                    val message = if (active) "Домен $domain включен" else "Домен $domain отключен"
                    _uiState.update { it.copy(
                        isLoading = false,
                        userMessage = message
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        userMessage = "Не удалось изменить статус домена $domain"
                    ) }
                }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    userMessage = "Ошибка при изменении статуса домена"
                ) }
                
                debugLogger.logError("RuTrackerDomainViewModel: Error setting domain active", e)
            }
        }
    }

    /**
     * Reset circuit breaker for domain
     */
    fun resetCircuitBreaker(domain: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val success = domainManager.resetCircuitBreaker(domain)
                if (success) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        userMessage = "Circuit breaker для $domain сброшен"
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        userMessage = "Не удалось сбросить circuit breaker для $domain"
                    ) }
                }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    userMessage = "Ошибка при сбросе circuit breaker"
                ) }
                
                debugLogger.logError("RuTrackerDomainViewModel: Error resetting circuit breaker", e)
            }
        }
    }

    /**
     * Get circuit breaker status for domain
     */
    fun getCircuitBreakerStatus(domain: String): String? {
        return runBlocking {
            try {
                domainManager.getCircuitBreakerStatus(domain)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error getting circuit breaker status", e)
                null
            }
        }
    }

    /**
     * Get domain statistics
     */
    fun getDomainStatistics(): Map<String, Map<String, Any>> {
        return runBlocking {
            try {
                domainManager.getDomainStatistics()
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error getting domain statistics", e)
                emptyMap()
            }
        }
    }

    /**
     * Mark error as resolved
     */
    fun markErrorAsResolved(errorId: String) {
        viewModelScope.launch {
            try {
                val success = errorHandler.markErrorAsResolved(errorId)
                if (success) {
                    _uiState.update { it.copy(
                        userMessage = "Ошибка помечена как решенная"
                    ) }
                } else {
                    _uiState.update { it.copy(
                        userMessage = "Не удалось пометить ошибку как решенную"
                    ) }
                }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error marking error as resolved", e)
            }
        }
    }

    /**
     * Clear all errors
     */
    fun clearAllErrors() {
        viewModelScope.launch {
            try {
                errorHandler.clearAllErrors()
                _uiState.update { it.copy(
                    userMessage = "Все ошибки очищены"
                ) }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error clearing all errors", e)
            }
        }
    }

    /**
     * Clear resolved errors
     */
    fun clearResolvedErrors() {
        viewModelScope.launch {
            try {
                errorHandler.clearResolvedErrors()
                _uiState.update { it.copy(
                    userMessage = "Решенные ошибки очищены"
                ) }
                
                // Clear user message after delay
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(userMessage = null) }
                
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error clearing resolved errors", e)
            }
        }
    }

    /**
     * Get errors by severity
     */
    fun getErrorsBySeverity(severity: ErrorSeverity): List<ErrorReport> {
        return runBlocking {
            try {
                errorHandler.getErrorsBySeverity(severity)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error getting errors by severity", e)
                emptyList()
            }
        }
    }

    /**
     * Get errors by domain
     */
    fun getErrorsByDomain(domain: String): List<ErrorReport> {
        return runBlocking {
            try {
                errorHandler.getErrorsByDomain(domain)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerDomainViewModel: Error getting errors by domain", e)
                emptyList()
            }
        }
    }

    /**
     * Clear user message
     */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * Get formatted last updated time
     */
    fun getFormattedLastUpdated(): String {
        val lastUpdated = _uiState.value.lastUpdated
        return if (lastUpdated > 0) {
            val now = System.currentTimeMillis()
            val diff = now - lastUpdated
            
            when {
                diff < 60_000 -> "Только что"
                diff < 300_000 -> "${diff / 60_000} мин. назад"
                diff < 3_600_000 -> "${diff / 60_000} мин. назад"
                diff < 86_400_000 -> "${diff / 3_600_000} ч. назад"
                else -> "${diff / 86_400_000} дн. назад"
            }
        } else {
            "Никогда"
        }
    }

    /**
     * Get domain health summary
     */
    fun getDomainHealthSummary(): Map<String, Int> {
        val statuses = _uiState.value.domainStatuses
        return mapOf(
            "total" to statuses.size,
            "available" to statuses.values.count { it.isAvailable },
            "unavailable" to statuses.values.count { !it.isAvailable },
            "healthy" to statuses.values.count { it.isAvailable && it.consecutiveFailures == 0 },
            "warning" to statuses.values.count { it.isAvailable && it.consecutiveFailures > 0 },
            "critical" to statuses.values.count { !it.isAvailable && it.consecutiveFailures >= 5 }
        )
    }
}