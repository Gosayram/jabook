package com.jabook.app.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jabook.app.core.data.network.model.AudiobookSearchResult
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.presentation.viewmodel.RuTrackerOfflineViewModel

/**
 * RuTracker Offline Management Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuTrackerOfflineManagementScreen(
  viewModel: RuTrackerOfflineViewModel = hiltViewModel(),
  onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val isOfflineMode by viewModel.isOfflineMode.collectAsState()
  val offlineDataStatus by viewModel.offlineDataStatus.collectAsState()
  val searchAnalytics by viewModel.offlineSearchAnalytics.collectAsState()
  val searchResults by viewModel.searchResults.collectAsState()
  val categories by viewModel.categories.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()

  var showSearchDialog by remember { mutableStateOf(false) }
  var showDataSummary by remember { mutableStateOf(false) }
  var showClearConfirmation by remember { mutableStateOf(false) }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      // TODO: show snackbar, log, etc.
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Offline Management") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        actions = {
          IconButton(onClick = { showDataSummary = true }) {
            Icon(Icons.Filled.Info, contentDescription = "Data Summary")
          }
        },
      )
    },
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
    ) {
      // Offline Mode Toggle
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Offline Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (isOfflineMode) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall, // <-- fixed typo
                color = if (isOfflineMode) Color.Green else Color.Gray,
              )
            }
            Switch(
              checked = isOfflineMode,
              onCheckedChange = { viewModel.toggleOfflineMode() },
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text =
              if (isOfflineMode)
                "Offline mode is active. You can search and browse cached content."
              else
                "Enable offline mode to access cached content without internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Data Status
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Offline Data Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
          )

          DataStatusItem(label = "Search Results", isAvailable = offlineDataStatus.hasSearchResults)
          DataStatusItem(label = "Categories", isAvailable = offlineDataStatus.hasCategories)
          DataStatusItem(label = "Torrent Details", isAvailable = offlineDataStatus.hasDetails)
          DataStatusItem(label = "Search Index", isAvailable = offlineDataStatus.hasSearchIndex)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { viewModel.loadOfflineData() },
          modifier = Modifier.weight(1f),
          enabled = !isLoading,
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Refresh")
          }
        }

        Button(
          onClick = { showSearchDialog = true },
          modifier = Modifier.weight(1f),
          enabled = isOfflineMode && offlineDataStatus.hasSearchResults,
        ) {
          Icon(Icons.Filled.Search, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Search")
        }

        Button(
          onClick = { showClearConfirmation = true },
          modifier = Modifier.weight(1f),
          enabled = !isLoading,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Icon(Icons.Filled.Delete, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Clear")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Search Analytics
      if (isOfflineMode) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Search Analytics",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp),
            )

            AnalyticsItem(label = "Total Searches", value = searchAnalytics.totalSearches.toString())
            AnalyticsItem(label = "Successful Searches", value = searchAnalytics.successfulSearches.toString())
            AnalyticsItem(label = "Failed Searches", value = searchAnalytics.failedSearches.toString())
            AnalyticsItem(label = "Total Results", value = searchAnalytics.totalResults.toString())

            if (searchAnalytics.totalSearches > 0) {
              val successRate =
                (searchAnalytics.successfulSearches.toFloat() / searchAnalytics.totalSearches) * 100
              AnalyticsItem(
                label = "Success Rate",
                value = "%.1f%%".format(successRate),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }

      // Recent Search Results
      if (searchResults.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Recent Search Results",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(modifier = Modifier.height(200.dp)) {
              items(searchResults.take(5)) { result ->
                SearchResultItem(result = result)
              }
            }
          }
        }
      }
    }
  }

  // Search Dialog
  if (showSearchDialog) {
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showSearchDialog = false },
      title = { Text("Search Offline") },
      text = {
        Column {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Query") },
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(modifier = Modifier.height(16.dp))

          if (searchResults.isNotEmpty()) {
            Text(
              text = "Found ${searchResults.size} results",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.searchOffline(searchQuery)
            showSearchDialog = false
          },
          enabled = searchQuery.isNotBlank(),
        ) {
          Text("Search")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSearchDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  // Data Summary Dialog
  if (showDataSummary) {
    val stats = viewModel.getOfflineDataStatistics()

    AlertDialog(
      onDismissRequest = { showDataSummary = false },
      title = { Text("Offline Data Summary") },
      text = {
        Column {
          SummaryItem(label = "Search Results", value = stats.searchResultsCount.toString())
          SummaryItem(label = "Categories", value = stats.categoriesCount.toString())
          SummaryItem(label = "Torrent Details", value = stats.detailsCount.toString())
          SummaryItem(label = "Search Index Size", value = stats.searchIndexSize.toString())
          SummaryItem(label = "Cache Size", value = formatFileSize(stats.totalCacheSize))
          SummaryItem(label = "Last Updated", value = formatTimestamp(stats.lastUpdated))
        }
      },
      confirmButton = {
        Button(onClick = { showDataSummary = false }) {
          Text("OK")
        }
      },
    )
  }

  // Clear Confirmation Dialog
  if (showClearConfirmation) {
    AlertDialog(
      onDismissRequest = { showClearConfirmation = false },
      title = { Text("Clear Offline Data") },
      text = { Text("Are you sure you want to clear all offline data? This action cannot be undone.") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearOfflineData()
            showClearConfirmation = false
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text("Clear")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmation = false }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
fun DataStatusItem(
  label: String,
  isAvailable: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
    Icon(
      imageVector = if (isAvailable) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
      contentDescription = null,
      tint = if (isAvailable) Color.Green else Color.Red,
      modifier = Modifier.size(16.dp),
    )
  }
}

@Composable
fun AnalyticsItem(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodySmall)
    Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun SummaryItem(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
    Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun SearchResultItem(
  result: Any,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      when (result) {
        is AudiobookSearchResult -> {
          Text(
            text = result.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Author: ${result.author}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(text = "Size: ${result.size}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Seeders: ${result.seeds}", style = MaterialTheme.typography.bodySmall)
          }
        }
        is RuTrackerSearchResult -> {
          if (result.results.isNotEmpty()) {
            val audiobook = result.results.first()
            Text(
              text = audiobook.title,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Author: ${audiobook.author}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(text = "Size: ${audiobook.size}", style = MaterialTheme.typography.bodySmall)
              Text(text = "Seeders: ${audiobook.seeders}", style = MaterialTheme.typography.bodySmall)
            }
          } else {
            Text(
              text = "No results found",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        else -> {
          Text(
            text = "Unknown result type",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String =
  when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
  }

private fun formatTimestamp(timestamp: Long): String =
  if (timestamp > 0) {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    when {
      diff < 60 * 1000 -> "Just now"
      diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
      diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
      else -> "${diff / (24 * 60 * 60 * 1000)} days ago"
    }
  } else {
    "Never"
  }
