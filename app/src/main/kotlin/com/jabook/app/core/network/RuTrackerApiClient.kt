package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker API Client with dual mode support
 *
 * Guest Mode: Browse and view magnet links without registration
 * Authenticated Mode: Full access with user credentials
 */
interface RuTrackerApiClient {
  // Guest mode operations
  suspend fun searchGuest(
    query: String,
    category: String? = null,
    page: Int = 1,
  ): Result<RuTrackerSearchResult>

  suspend fun getCategoriesGuest(): Result<List<RuTrackerCategory>>

  suspend fun getAudiobookDetailsGuest(topicId: String): Result<RuTrackerAudiobook?>

  suspend fun getMagnetLinkGuest(topicId: String): Result<String?>

  suspend fun getTopAudiobooksGuest(limit: Int = 20): Result<List<RuTrackerAudiobook>>

  suspend fun getNewAudiobooksGuest(limit: Int = 20): Result<List<RuTrackerAudiobook>>

  // Authenticated mode operations
  suspend fun login(
    username: String,
    password: String,
  ): Result<Boolean>

  suspend fun searchAuthenticated(
    query: String,
    sort: String = "seeds",
    order: String = "desc",
    page: Int = 1,
  ): Result<RuTrackerSearchResult>

  suspend fun downloadTorrent(topicId: String): Result<InputStream?>

  suspend fun getMagnetLinkAuthenticated(topicId: String): Result<String?>

  suspend fun getTorrentDetailsAuthenticated(topicId: String): Result<RuTrackerAudiobook?>

  suspend fun logout(): Result<Boolean>

  // Common operations
  fun isAuthenticated(): Boolean

  fun getCurrentUser(): String?

  suspend fun checkAvailability(): Result<Boolean>

  fun getAuthenticationState(): Flow<AuthenticationState>
}

/**
 * Authentication state for RuTracker
 */
sealed class AuthenticationState {
  object Guest : AuthenticationState()

  data class Authenticated(
    val username: String,
  ) : AuthenticationState()

  data class Error(
    val message: String,
  ) : AuthenticationState()

  object Loading : AuthenticationState()
}

/**
 * RuTracker API Client implementation
 */
@Singleton
class RuTrackerApiClientImpl
  @Inject
  constructor(
    private val ruTrackerApiService: RuTrackerApiService,
    private val ruTrackerParser: RuTrackerParser,
    private val ruTrackerPreferences: RuTrackerPreferences,
    private val debugLogger: IDebugLogger,
  ) : RuTrackerApiClient {
    private var currentUser: String? = null
    private var isLoggedIn = false

    override suspend fun searchGuest(
      query: String,
      category: String?,
      page: Int,
    ): Result<RuTrackerSearchResult> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Searching guest mode - query: $query, category: $category, page: $page")

        val responseResult = ruTrackerApiService.searchGuest(query, category, page)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Search failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val audiobooks = ruTrackerParser.parseSearchResults(response)

        Result.success(
          RuTrackerSearchResult(
            query = query,
            totalResults = audiobooks.size,
            currentPage = page,
            totalPages = 1, // Pagination parsing not implemented, always 1 page
            results = audiobooks,
          ),
        )
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Guest search failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getCategoriesGuest(): Result<List<RuTrackerCategory>> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting categories guest mode")

        val responseResult = ruTrackerApiService.getCategoriesGuest()
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Categories failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val categories = ruTrackerParser.parseCategories(response)

        Result.success(categories)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Guest categories failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getAudiobookDetailsGuest(topicId: String): Result<RuTrackerAudiobook?> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting audiobook details guest mode - topicId: $topicId")

        val responseResult = ruTrackerApiService.getAudiobookDetailsGuest(topicId)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Audiobook details failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val audiobook = ruTrackerParser.parseAudiobookDetails(response)

        Result.success(audiobook)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Guest audiobook details failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getMagnetLinkGuest(topicId: String): Result<String?> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting magnet link guest mode - topicId: $topicId")

        val responseResult = ruTrackerApiService.getAudiobookDetailsGuest(topicId)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Magnet link failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val magnetLink = ruTrackerParser.extractMagnetLink(response)

        Result.success(magnetLink)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Guest magnet link failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getTopAudiobooksGuest(limit: Int): Result<List<RuTrackerAudiobook>> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting top audiobooks guest mode - limit: $limit")

        val responseResult = ruTrackerApiService.getTopAudiobooksGuest(limit)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Top audiobooks failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val audiobooks = ruTrackerParser.parseSearchResults(response)

        Result.success(audiobooks)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Guest top audiobooks failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getNewAudiobooksGuest(limit: Int): Result<List<RuTrackerAudiobook>> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting new audiobooks guest mode - limit: $limit")

        val responseResult = ruTrackerApiService.getNewAudiobooksGuest(limit)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("New audiobooks failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val audiobooks = ruTrackerParser.parseSearchResults(response)

        Result.success(audiobooks)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Guest new audiobooks failed", e)
        Result.failure(e)
      }
    }

    override suspend fun login(
      username: String,
      password: String,
    ): Result<Boolean> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Attempting login - username: $username")

        val successResult = ruTrackerApiService.login(username, password)
        if (successResult.isFailure) {
          return Result.failure(successResult.exceptionOrNull() ?: Exception("Login failed"))
        }

        val success = successResult.getOrNull() ?: false
        if (success) {
          currentUser = username
          isLoggedIn = true
          ruTrackerPreferences.setCredentials(username, password)
          debugLogger.logInfo("RuTrackerApiClient: Login successful for user: $username")
        } else {
          debugLogger.logWarning("RuTrackerApiClient: Login failed for user: $username")
        }

        Result.success(success)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Login error", e)
        Result.failure(e)
      }
    }

    override suspend fun searchAuthenticated(
      query: String,
      sort: String,
      order: String,
      page: Int,
    ): Result<RuTrackerSearchResult> {
      return try {
        debugLogger.logDebug(
          "RuTrackerApiClient: Searching authenticated mode - query: $query, sort: $sort, order: $order, page: $page",
        )

        val responseResult = ruTrackerApiService.searchAuthenticated(query, sort, order, page)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Authenticated search failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val audiobooks = ruTrackerParser.parseSearchResults(response)

        Result.success(
          RuTrackerSearchResult(
            query = query,
            totalResults = audiobooks.size,
            currentPage = page,
            totalPages = 1, // Pagination parsing not implemented, always 1 page
            results = audiobooks,
          ),
        )
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Authenticated search failed", e)
        Result.failure(e)
      }
    }

    override suspend fun downloadTorrent(topicId: String): Result<InputStream?> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Downloading torrent - topicId: $topicId")

        val inputStreamResult = ruTrackerApiService.downloadTorrent(topicId)
        if (inputStreamResult.isFailure) {
          return Result.failure(inputStreamResult.exceptionOrNull() ?: Exception("Torrent download failed"))
        }

        Result.success(inputStreamResult.getOrNull())
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Torrent download failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getMagnetLinkAuthenticated(topicId: String): Result<String?> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting magnet link authenticated mode - topicId: $topicId")

        val responseResult = ruTrackerApiService.getAudiobookDetailsAuthenticated(topicId)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Authenticated magnet link failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val magnetLink = ruTrackerParser.extractMagnetLink(response)

        Result.success(magnetLink)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Authenticated magnet link failed", e)
        Result.failure(e)
      }
    }

    override suspend fun getTorrentDetailsAuthenticated(topicId: String): Result<RuTrackerAudiobook?> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Getting torrent details authenticated mode - topicId: $topicId")

        val responseResult = ruTrackerApiService.getAudiobookDetailsAuthenticated(topicId)
        if (responseResult.isFailure) {
          return Result.failure(responseResult.exceptionOrNull() ?: Exception("Authenticated torrent details failed"))
        }

        val response = responseResult.getOrNull() ?: ""
        val audiobook = ruTrackerParser.parseAudiobookDetails(response)

        Result.success(audiobook)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Authenticated torrent details failed", e)
        Result.failure(e)
      }
    }

    override suspend fun logout(): Result<Boolean> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Logging out")

        val successResult = ruTrackerApiService.logout()
        if (successResult.isFailure) {
          return Result.failure(successResult.exceptionOrNull() ?: Exception("Logout failed"))
        }

        val success = successResult.getOrNull() ?: false
        if (success) {
          currentUser = null
          isLoggedIn = false
          ruTrackerPreferences.clearCredentials()
          debugLogger.logInfo("RuTrackerApiClient: Logout successful")
        }

        Result.success(success)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Logout error", e)
        Result.failure(e)
      }
    }

    override fun isAuthenticated(): Boolean = isLoggedIn

    override fun getCurrentUser(): String? = currentUser

    override suspend fun checkAvailability(): Result<Boolean> {
      return try {
        debugLogger.logDebug("RuTrackerApiClient: Checking availability")

        val availableResult = ruTrackerApiService.checkAvailability()
        if (availableResult.isFailure) {
          return Result.failure(availableResult.exceptionOrNull() ?: Exception("Availability check failed"))
        }

        Result.success(availableResult.getOrNull() ?: false)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiClient: Availability check failed", e)
        Result.failure(e)
      }
    }

    override fun getAuthenticationState(): Flow<AuthenticationState> {
      // Returns current authentication state only, not reactive
      return kotlinx.coroutines.flow.flowOf(
        if (isLoggedIn) {
          AuthenticationState.Authenticated(currentUser ?: "")
        } else {
          AuthenticationState.Guest
        },
      )
    }
  }
