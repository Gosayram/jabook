package com.jabook.app.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.features.library.domain.usecase.GetLibraryAudiobooksUseCase
import com.jabook.app.features.library.domain.usecase.UpdateAudiobookUseCase
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel for the Library screen. Manages the state and handles user interactions for the audiobook library. */
@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val getLibraryAudiobooksUseCase: GetLibraryAudiobooksUseCase,
    private val updateAudiobookUseCase: UpdateAudiobookUseCase,
    private val debugLogger: IDebugLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val audiobooks: StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    init {
        debugLogger.logInfo("LibraryViewModel initialized")
        loadAudiobooks()
        loadCategories()
    }

    /** Load audiobooks based on current filter. */
    fun loadAudiobooks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val flow =
                    when (_uiState.value.currentFilter) {
                        LibraryFilter.ALL -> getLibraryAudiobooksUseCase()
                        LibraryFilter.FAVORITES -> getLibraryAudiobooksUseCase.getFavorites()
                        LibraryFilter.CURRENTLY_PLAYING -> getLibraryAudiobooksUseCase.getCurrentlyPlaying()
                        LibraryFilter.COMPLETED -> getLibraryAudiobooksUseCase.getCompleted()
                        LibraryFilter.DOWNLOADED -> getLibraryAudiobooksUseCase.getDownloaded()
                        LibraryFilter.CATEGORY -> {
                            val category = _uiState.value.selectedCategory
                            if (category != null) {
                                getLibraryAudiobooksUseCase.getByCategory(category)
                            } else {
                                getLibraryAudiobooksUseCase()
                            }
                        }
                    }

                flow
                    .catch { exception ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = exception.message ?: "Unknown error occurred")
                    }
                    .collect { books ->
                        _audiobooks.value = books
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    }
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = exception.message ?: "Unknown error occurred")
            }
        }
    }

    /** Load available categories. */
    private fun loadCategories() {
        viewModelScope.launch {
            getLibraryAudiobooksUseCase
                .getCategories()
                .catch { /* Ignore errors for categories */ }
                .collect { categoryList -> _categories.value = categoryList }
        }
    }

    /** Search audiobooks by query. */
    fun searchAudiobooks(query: String) {
        if (query.isBlank()) {
            loadAudiobooks()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            getLibraryAudiobooksUseCase
                .search(query)
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = exception.message ?: "Search failed")
                }
                .collect { books ->
                    _audiobooks.value = books
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }
        }
    }

    /** Change the current filter. */
    fun changeFilter(filter: LibraryFilter, category: String? = null) {
        _uiState.value = _uiState.value.copy(currentFilter = filter, selectedCategory = category)
        loadAudiobooks()
    }

    /** Toggle favorite status of an audiobook. */
    fun toggleFavorite(audiobook: Audiobook) {
        viewModelScope.launch {
            try {
                updateAudiobookUseCase.toggleFavorite(audiobook.id, !audiobook.isFavorite)
                // UI will update automatically through Flow
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to update favorite: ${exception.message}")
            }
        }
    }

    /** Update user rating for an audiobook. */
    fun updateRating(audiobook: Audiobook, rating: Float) {
        viewModelScope.launch {
            try {
                updateAudiobookUseCase.updateRating(audiobook.id, rating)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to update rating: ${exception.message}")
            }
        }
    }

    /** Mark audiobook as completed. */
    fun markAsCompleted(audiobook: Audiobook) {
        viewModelScope.launch {
            try {
                updateAudiobookUseCase.markAsCompleted(audiobook.id)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to mark as completed: ${exception.message}")
            }
        }
    }

    /** Reset playback for an audiobook. */
    fun resetPlayback(audiobook: Audiobook) {
        viewModelScope.launch {
            try {
                updateAudiobookUseCase.resetPlayback(audiobook.id)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to reset playback: ${exception.message}")
            }
        }
    }

    /** Clear any current error. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/** UI state for the Library screen. */
data class LibraryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: LibraryFilter = LibraryFilter.ALL,
    val selectedCategory: String? = null,
)

/** Available filters for the library view. */
enum class LibraryFilter {
    ALL,
    FAVORITES,
    CURRENTLY_PLAYING,
    COMPLETED,
    DOWNLOADED,
    CATEGORY,
}
