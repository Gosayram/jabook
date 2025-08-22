package com.jabook.app.features.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.repository.RuTrackerRepository
import com.jabook.app.core.network.RuTrackerPreferences
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel
  @Inject
  constructor(
    private val ruTrackerRepository: RuTrackerRepository,
    private val debugLogger: IDebugLogger,
    private val ruTrackerPreferences: RuTrackerPreferences,
  ) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RuTrackerAudiobook>>(emptyList())
    val searchResults: StateFlow<List<RuTrackerAudiobook>> = _searchResults.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _categories = MutableStateFlow<List<RuTrackerCategory>>(emptyList())
    val categories: StateFlow<List<RuTrackerCategory>> = _categories.asStateFlow()

    private val _trendingAudiobooks = MutableStateFlow<List<RuTrackerAudiobook>>(emptyList())
    val trendingAudiobooks: StateFlow<List<RuTrackerAudiobook>> = _trendingAudiobooks.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<RuTrackerAudiobook>>(emptyList())
    val recentlyAdded: StateFlow<List<RuTrackerAudiobook>> = _recentlyAdded.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    val isGuestMode: StateFlow<Boolean> =
      ruTrackerPreferences.getGuestModeFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
      )

    val uiState: StateFlow<DiscoveryUiState> =
      combine(
        searchQuery,
        selectedCategory,
        currentPage,
        isLoading,
        searchResults,
        totalPages,
        categories,
        trendingAudiobooks,
        recentlyAdded,
        errorMessage,
        isSearchActive,
        isGuestMode, // добавляем
      ) { states ->
        DiscoveryUiState(
          searchQuery = states[0] as String,
          selectedCategory = states[1] as String?,
          currentPage = states[2] as Int,
          isLoading = states[3] as Boolean,
          searchResults = states[4] as List<RuTrackerAudiobook>,
          totalPages = states[5] as Int,
          categories = states[6] as List<RuTrackerCategory>,
          trendingAudiobooks = states[7] as List<RuTrackerAudiobook>,
          recentlyAdded = states[8] as List<RuTrackerAudiobook>,
          errorMessage = states[9] as String?,
          isSearchActive = states[10] as Boolean,
          isGuestMode = states[11] as Boolean,
        )
      }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = DiscoveryUiState())

    init {
      debugLogger.logInfo("DiscoveryViewModel initialized")
      loadInitialData()
    }

    private fun loadInitialData() {
      viewModelScope.launch {
        try {
          loadCategories()
          loadTrendingAudiobooks()
          loadRecentlyAdded()
        } catch (e: Exception) {
          debugLogger.logError("Error loading initial discovery data", e)
          _errorMessage.value = "Failed to load initial data: ${e.message}"
        }
      }
    }

    fun updateSearchQuery(query: String) {
      _searchQuery.value = query
      if (query.isBlank()) {
        _isSearchActive.value = false
        _searchResults.value = emptyList()
      }
    }

    fun selectCategory(categoryId: String?) {
      _selectedCategory.value = categoryId
      if (_searchQuery.value.isNotBlank()) {
        performSearch()
      }
    }

    fun performSearch() {
      val query = _searchQuery.value.trim()
      if (query.isBlank()) return

      viewModelScope.launch {
        try {
          _isLoading.value = true
          _isSearchActive.value = true
          _errorMessage.value = null
          _currentPage.value = 1

          debugLogger.logInfo("Performing search: query='$query', category='${_selectedCategory.value}'")

          ruTrackerRepository
            .searchAudiobooks(query = query, categoryId = _selectedCategory.value, page = 1)
            .catch { error ->
              debugLogger.logError("Search error", error)
              _errorMessage.value = "Search failed: ${error.message}"
              _isLoading.value = false
            }.collect { searchResult ->
              _searchResults.value = searchResult.results
              _totalPages.value = searchResult.totalPages
              _isLoading.value = false

              debugLogger.logInfo("Search completed: ${searchResult.results.size} results, ${searchResult.totalPages} pages")
            }
        } catch (e: Exception) {
          debugLogger.logError("Search error", e)
          _errorMessage.value = "Search failed: ${e.message}"
          _isLoading.value = false
        }
      }
    }

    fun loadNextPage() {
      if (_isLoading.value || _currentPage.value >= _totalPages.value) return

      val query = _searchQuery.value.trim()
      if (query.isBlank()) return

      viewModelScope.launch {
        try {
          _isLoading.value = true
          val nextPage = _currentPage.value + 1

          debugLogger.logInfo("Loading page $nextPage")

          ruTrackerRepository
            .searchAudiobooks(query = query, categoryId = _selectedCategory.value, page = nextPage)
            .catch { error ->
              debugLogger.logError("Page load error", error)
              _errorMessage.value = "Failed to load page: ${error.message}"
              _isLoading.value = false
            }.collect { searchResult ->
              _searchResults.value = _searchResults.value + searchResult.results
              _currentPage.value = nextPage
              _isLoading.value = false

              debugLogger.logInfo("Page $nextPage loaded: ${searchResult.results.size} new results")
            }
        } catch (e: Exception) {
          debugLogger.logError("Page load error", e)
          _errorMessage.value = "Failed to load page: ${e.message}"
          _isLoading.value = false
        }
      }
    }

    fun clearSearch() {
      _searchQuery.value = ""
      _isSearchActive.value = false
      _searchResults.value = emptyList()
      _currentPage.value = 1
      _errorMessage.value = null
    }

    fun dismissError() {
      _errorMessage.value = null
    }

    private suspend fun loadCategories() {
      ruTrackerRepository
        .getCategories()
        .catch { error -> debugLogger.logError("Failed to load categories", error) }
        .collect { categories ->
          _categories.value = categories
          debugLogger.logInfo("Loaded ${categories.size} categories")
        }
    }

    private suspend fun loadTrendingAudiobooks() {
      ruTrackerRepository
        .getTrendingAudiobooks(limit = 10)
        .catch { error -> debugLogger.logError("Failed to load trending audiobooks", error) }
        .collect { audiobooks ->
          _trendingAudiobooks.value = audiobooks
          debugLogger.logInfo("Loaded ${audiobooks.size} trending audiobooks")
        }
    }

    private suspend fun loadRecentlyAdded() {
      ruTrackerRepository
        .getRecentlyAdded(limit = 10)
        .catch { error -> debugLogger.logError("Failed to load recently added audiobooks", error) }
        .collect { audiobooks ->
          _recentlyAdded.value = audiobooks
          debugLogger.logInfo("Loaded ${audiobooks.size} recently added audiobooks")
        }
    }

    fun refreshData() {
      viewModelScope.launch {
        try {
          _isLoading.value = true
          loadInitialData()
          _isLoading.value = false
        } catch (e: Exception) {
          debugLogger.logError("Error refreshing data", e)
          _errorMessage.value = "Failed to refresh data: ${e.message}"
          _isLoading.value = false
        }
      }
    }
  }

data class DiscoveryUiState(
  val searchQuery: String = "",
  val selectedCategory: String? = null,
  val currentPage: Int = 1,
  val isLoading: Boolean = false,
  val searchResults: List<RuTrackerAudiobook> = emptyList(),
  val totalPages: Int = 1,
  val categories: List<RuTrackerCategory> = emptyList(),
  val trendingAudiobooks: List<RuTrackerAudiobook> = emptyList(),
  val recentlyAdded: List<RuTrackerAudiobook> = emptyList(),
  val errorMessage: String? = null,
  val isSearchActive: Boolean = false,
  val isGuestMode: Boolean = true,
)
