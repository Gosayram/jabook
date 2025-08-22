package com.jabook.app.features.library.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.R
import com.jabook.app.features.library.presentation.LibraryFilter

/** Component displaying filter chips for the library view. Allows users to filter audiobooks by different criteria. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterChips(
  currentFilter: LibraryFilter,
  categories: List<String>,
  selectedCategory: String?,
  onFilterChange: (LibraryFilter, String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCategoryDropdown by remember { mutableStateOf(false) }

  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // All filter
    FilterChip(
      selected = currentFilter == LibraryFilter.ALL,
      onClick = { onFilterChange(LibraryFilter.ALL, null) },
      label = { Text(stringResource(R.string.filter_all)) },
    )

    // Favorites filter
    FilterChip(
      selected = currentFilter == LibraryFilter.FAVORITES,
      onClick = { onFilterChange(LibraryFilter.FAVORITES, null) },
      label = { Text(stringResource(R.string.filter_favorites)) },
    )

    // Currently Playing filter
    FilterChip(
      selected = currentFilter == LibraryFilter.CURRENTLY_PLAYING,
      onClick = { onFilterChange(LibraryFilter.CURRENTLY_PLAYING, null) },
      label = { Text(stringResource(R.string.filter_currently_playing)) },
    )

    // Completed filter
    FilterChip(
      selected = currentFilter == LibraryFilter.COMPLETED,
      onClick = { onFilterChange(LibraryFilter.COMPLETED, null) },
      label = { Text(stringResource(R.string.filter_completed)) },
    )

    // Downloaded filter
    FilterChip(
      selected = currentFilter == LibraryFilter.DOWNLOADED,
      onClick = { onFilterChange(LibraryFilter.DOWNLOADED, null) },
      label = { Text(stringResource(R.string.filter_downloaded)) },
    )

    // Category filter with dropdown
    if (categories.isNotEmpty()) {
      FilterChip(
        selected = currentFilter == LibraryFilter.CATEGORY,
        onClick = { showCategoryDropdown = true },
        label = { Text(text = selectedCategory ?: stringResource(R.string.discovery_categories)) },
      )

      DropdownMenu(expanded = showCategoryDropdown, onDismissRequest = { showCategoryDropdown = false }) {
        for (category in categories) {
          DropdownMenuItem(
            text = { Text(category) },
            onClick = {
              onFilterChange(LibraryFilter.CATEGORY, category)
              showCategoryDropdown = false
            },
          )
        }
      }
    }
  }
}
