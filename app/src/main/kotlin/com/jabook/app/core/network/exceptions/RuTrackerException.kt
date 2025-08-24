package com.jabook.app.core.network.exceptions

/**
 * Base exception for all RuTracker-related errors
 */
sealed class RuTrackerException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause) {
  /**
   * Service is temporarily unavailable
   */
  class ServiceUnavailableException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Network connectivity issues
   */
  class NetworkException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Parsing errors when processing HTML responses
   */
  class ParseException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Authentication failures
   */
  class AuthenticationException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Rate limiting exceeded
   */
  class RateLimitException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Invalid or malformed request
   */
  class InvalidRequestException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Resource not found
   */
  class NotFoundException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * All domains are unavailable
   */
  class AllDomainsUnavailableException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Circuit breaker is open (service temporarily blocked)
   */
  class CircuitBreakerOpenException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Cache-related errors
   */
  class CacheException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Offline mode errors
   */
  class OfflineException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Domain-specific errors
   */
  class DomainException(
    val domain: String,
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Search functionality is unavailable
   */
  class SearchUnavailableException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Parsing timeout
   */
  class ParseTimeoutException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Invalid response format
   */
  class InvalidResponseException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Configuration errors
   */
  class ConfigurationException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Security-related errors
   */
  class SecurityException(
    message: String,
    cause: Throwable? = null,
  ) : RuTrackerException(message, cause)

  /**
   * Helper function to create appropriate exception based on error type
   */
  companion object {
    fun fromErrorType(
      errorType: String,
      message: String,
      cause: Throwable? = null,
    ): RuTrackerException =
      when (errorType.lowercase()) {
        "network" -> NetworkException(message, cause)
        "parse" -> ParseException(message, cause)
        "auth" -> AuthenticationException(message, cause)
        "rate_limit" -> RateLimitException(message, cause)
        "not_found" -> NotFoundException(message, cause)
        "service_unavailable" -> ServiceUnavailableException(message, cause)
        "circuit_breaker" -> CircuitBreakerOpenException(message, cause)
        "cache" -> CacheException(message, cause)
        "offline" -> OfflineException(message, cause)
        "search_unavailable" -> SearchUnavailableException(message, cause)
        "parse_timeout" -> ParseTimeoutException(message, cause)
        "invalid_response" -> InvalidResponseException(message, cause)
        "configuration" -> ConfigurationException(message, cause)
        "security" -> SecurityException(message, cause)
        else -> ServiceUnavailableException(message, cause)
      }
  }
}
