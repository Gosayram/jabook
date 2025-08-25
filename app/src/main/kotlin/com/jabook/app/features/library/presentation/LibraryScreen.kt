package com.jabook.app.features.library.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jabook.app.R
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.features.library.presentation.components.AudiobookListItem
import com.jabook.app.features.library.presentation.components.LibraryFilterChips
import com.jabook.app.shared.ui.AppThemeMode
import com.jabook.app.shared.ui.ThemeViewModel
import com.jabook.app.shared.ui.components.ThemeToggleButton
import com.jabook.app.shared.ui.theme.JaBookTheme

/** Main Library screen showing the user's audiobook collection. Supports filtering, searching, and basic audiobook management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  onAudiobookClick: (Audiobook) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LibraryViewModel = hiltViewModel(),
  themeViewModel: ThemeViewModel,
  themeMode: AppThemeMode,
) {
  val uiState by viewModel.uiState.collectAsState()
  val audiobooks by viewModel.audiobooks.collectAsState()
  val categories by viewModel.categories.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var isSearchActive by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  // Handle error messages
  LaunchedEffect(uiState.error) {
    uiState.error?.let { error ->
      snackbarHostState.showSnackbar(error)
      viewModel.clearError()
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    // Reduced vertical padding
    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top,
  ) {
    TopAppBar(
      title = { Text(stringResource(R.string.library_title)) },
      actions = {
        IconButton(onClick = { isSearchActive = !isSearchActive }) {
          Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.search_audiobooks))
        }
        ThemeToggleButton(themeMode = themeMode, onToggle = { themeViewModel.toggleTheme() })
      },
    )

    if (isSearchActive) {
      androidx.compose.material3.SearchBar(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        onSearch = {
          viewModel.searchAudiobooks(searchQuery)
          isSearchActive = false
        },
        active = isSearchActive,
        onActiveChange = { isSearchActive = it },
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp), // Reduced padding
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.search_audiobooks)
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search"
              )
            }
          }
        }
      ) {
        // Search suggestions can be added here
      }
    }

    LibraryFilterChips(
      currentFilter = uiState.currentFilter,
      categories = categories,
      selectedCategory = uiState.selectedCategory,
      onFilterChange = viewModel::changeFilter,
      modifier = Modifier.padding(vertical = 4.dp), // Reduced vertical padding
    )

    // Removed unnecessary Spacer

    Box(modifier = Modifier.weight(1f)) {
      when {
        uiState.isLoading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        audiobooks.isEmpty() -> {
          EmptyLibraryMessage(filter = uiState.currentFilter, modifier = Modifier.align(Alignment.Center))
        }
        else -> {
          LazyColumn(
            contentPadding =
              androidx.compose.foundation.layout
                .PaddingValues(vertical = 8.dp),
            verticalArrangement =
              androidx.compose.foundation.layout.Arrangement
                .spacedBy(12.dp),
          ) {
            items(audiobooks) { audiobook ->
              AudiobookListItem(
                audiobook = audiobook,
                onClick = { onAudiobookClick(audiobook) },
                onFavoriteClick = { viewModel.toggleFavorite(audiobook) },
                onRatingChange = { rating -> viewModel.updateRating(audiobook, rating) },
                onMarkCompleted = { viewModel.markAsCompleted(audiobook) },
                onResetPlayback = { viewModel.resetPlayback(audiobook) },
                modifier = Modifier.padding(horizontal = 0.dp),
              )
            }
          }
        }
      }
    }
  }

  SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(16.dp))
}

@Composable
private fun EmptyLibraryMessage(
  filter: LibraryFilter,
  modifier: Modifier = Modifier,
) {
  val messageResId =
    when (filter) {
      LibraryFilter.ALL -> R.string.empty_library_message
      LibraryFilter.FAVORITES -> R.string.empty_favorites_message
      LibraryFilter.CURRENTLY_PLAYING -> R.string.empty_currently_playing_message
      LibraryFilter.COMPLETED -> R.string.empty_completed_message
      LibraryFilter.DOWNLOADED -> R.string.empty_downloaded_message
      LibraryFilter.CATEGORY -> R.string.empty_category_message
    }

  Text(
    text = stringResource(messageResId),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(32.dp),
  )
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
  JaBookTheme {
    // For preview, we need to provide a ThemeViewModel instance
    LibraryScreen(
      onAudiobookClick = {},
      themeViewModel = TODO("Preview does not provide ThemeViewModel"),
      themeMode = AppThemeMode.SYSTEM,
    )
  }
}
