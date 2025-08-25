package com.jabook.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.cache.CacheStatistics
import com.jabook.app.presentation.viewmodel.RuTrackerCacheViewModel

/**
 * Cache statistics card component
 */
@Composable
fun CacheStatisticsCard(
  statistics: CacheStatistics,
  hitRate: String,
  cacheSize: String,
  memorySize: String,
  diskSize: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
    ) {
      Text(
        text = "Статистика кэша",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        StatisticItem(
          label = "Hit Rate",
          value = hitRate,
          color = MaterialTheme.colorScheme.primary,
        )

        StatisticItem(
          label = "Всего размер",
          value = cacheSize,
          color = MaterialTheme.colorScheme.secondary,
        )

        StatisticItem(
          label = "Память",
          value = memorySize,
          color = MaterialTheme.colorScheme.tertiary,
        )

        StatisticItem(
          label = "Диск",
          value = diskSize,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        StatisticItem(
          label = "Записи в памяти",
          value = statistics.memoryEntries.toString(),
          color = MaterialTheme.colorScheme.primary,
        )

        StatisticItem(
          label = "Записи на диске",
          value = statistics.diskEntries.toString(),
          color = MaterialTheme.colorScheme.secondary,
        )

        StatisticItem(
          label = "Попаданий",
          value = statistics.hitCount.toString(),
          color = Color.Green,
        )

        StatisticItem(
          label = "Промахов",
          value = statistics.missCount.toString(),
          color = Color.Red,
        )
      }
    }
  }
}

/**
 * Statistic item component
 */
@Composable
fun StatisticItem(
  label: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      color = color,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Efficiency metrics card component
 */
@Composable
fun EfficiencyMetricsCard(
  metrics: Map<String, Float>,
  getMetricValue: (String) -> String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
    ) {
      Text(
        text = "Эффективность кэша",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
      ) {
        items(metrics.keys.toList()) { metric ->
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                when (metric) {
                  "hitRate" -> "Hit Rate"
                  "memoryUtilization" -> "Использование памяти"
                  "diskUtilization" -> "Использование диска"
                  "evictionRate" -> "Rate Eviction"
                  else -> metric
                },
              style = MaterialTheme.typography.bodyMedium,
            )

            Box(
              modifier =
                Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(
                    when {
                      metric == "hitRate" && metrics[metric] ?: 0f > 0.8f -> Color.Green
                      metric == "hitRate" && metrics[metric] ?: 0f > 0.5f -> Color.Yellow
                      metric == "hitRate" -> Color.Red
                      metrics[metric] ?: 0f > 0.8f -> Color.Red
                      metrics[metric] ?: 0f > 0.5f -> Color.Yellow
                      else -> Color.Green
                    },
                  ).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
              Text(
                text = getMetricValue(metric),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Cache configuration card component
 */
@Composable
fun CacheConfigurationCard(
  configSummary: Map<String, String>,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
    ) {
      Text(
        text = "Конфигурация кэша",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
      ) {
        items(configSummary.keys.toList()) { key ->
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                when (key) {
                  "memoryMaxSize" -> "Макс. размер памяти"
                  "diskMaxSize" -> "Макс. размер диска"
                  "defaultTTL" -> "TTL по умолчанию"
                  "cleanupInterval" -> "Интервал очистки"
                  "compressionEnabled" -> "Сжатие"
                  "encryptionEnabled" -> "Шифрование"
                  else -> key
                },
              style = MaterialTheme.typography.bodyMedium,
            )

            Text(
              text = configSummary[key] ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

/**
 * Cache key item component
 */
@Composable
fun CacheKeyItem(
  key: String,
  onRemove: (String) -> Unit,
  onViewDetails: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = key,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )

      Row {
        IconButton(onClick = { onViewDetails(key) }) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "View details",
            tint = MaterialTheme.colorScheme.primary,
          )
        }

        IconButton(onClick = { onRemove(key) }) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Remove",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

/**
 * Main cache management screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuTrackerCacheManagementScreen(
  viewModel: RuTrackerCacheViewModel = hiltViewModel(),
  onNavigateBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  var showConfigDialog by remember { mutableStateOf<Boolean>(false) }
  var showDebugDialog by remember { mutableStateOf<Boolean>(false) }
  var selectedKey by remember { mutableStateOf<String>("") }
  var debugKey by remember { mutableStateOf<String>("") }
  var debugValue by remember { mutableStateOf<String>("") }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(uiState.selectedNamespace) {
    viewModel.loadCacheKeys()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Управление кэшем RuTracker") },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { viewModel.refreshCacheData() }) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = if (uiState.isRefreshing) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
          }
          IconButton(onClick = { showConfigDialog = true }) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Configuration",
            )
          }
          IconButton(onClick = { showDebugDialog = true }) {
            Icon(
              imageVector = Icons.Default.Build,
              contentDescription = "Debug",
            )
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(16.dp),
    ) {
      // Cache statistics
      CacheStatisticsCard(
        statistics = uiState.statistics,
        hitRate = viewModel.getHitRatePercentage(),
        cacheSize = viewModel.getFormattedCacheSize(),
        memorySize = viewModel.getFormattedMemorySize(),
        diskSize = viewModel.getFormattedDiskSize(),
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Efficiency metrics
      EfficiencyMetricsCard(
        metrics = uiState.efficiencyMetrics,
        getMetricValue = { viewModel.getEfficiencyMetricValue(it) },
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Namespace selection and actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Namespace selector
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Пространство:",
            style = MaterialTheme.typography.bodyMedium,
          )

          Spacer(modifier = Modifier.width(8.dp))

          var expanded by remember { mutableStateOf<Boolean>(false) }
          Box {
            OutlinedButton(onClick = { expanded = true }) {
              Text(uiState.selectedNamespace)
              Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
            ) {
              uiState.availableNamespaces.forEach { namespace ->
                DropdownMenuItem(
                  text = { Text(namespace) },
                  onClick = {
                    viewModel.selectNamespace(namespace)
                    expanded = false
                  },
                )
              }
            }
          }
        }

        // Action buttons
        Row {
          OutlinedButton(onClick = { viewModel.forceCleanup() }) {
            Text("Очистить")
          }

          Spacer(modifier = Modifier.width(8.dp))

          Button(onClick = { viewModel.clearNamespaceCache() }) {
            Text("Очистить все")
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Cache keys list
      if (uiState.isLoading) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      } else if (uiState.cacheKeys.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Нет записей в кэше",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
        ) {
          items(uiState.cacheKeys) { key ->
            CacheKeyItem(
              key = key,
              onRemove = { viewModel.removeCacheEntry(it) },
              onViewDetails = {
                selectedKey = it
                // TODO: Show details dialog
              },
            )
          }
        }
      }

      // User message
      uiState.userMessage?.let { message ->
        Spacer(modifier = Modifier.height(16.dp))
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
          Text(
            text = message,
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center,
          )
        }

        LaunchedEffect(message) {
          kotlinx.coroutines.delay(3000)
          viewModel.clearUserMessage()
        }
      }
    }
  }

  // Configuration dialog
  if (showConfigDialog) {
    AlertDialog(
      onDismissRequest = { showConfigDialog = false },
      title = { Text("Конфигурация кэша") },
      text = {
        CacheConfigurationCard(
          configSummary = viewModel.getConfigSummary(),
        )
      },
      confirmButton = {
        TextButton(onClick = { showConfigDialog = false }) {
          Text("Закрыть")
        }
      },
    )
  }

  // Debug dialog
  if (showDebugDialog) {
    AlertDialog(
      onDismissRequest = { showDebugDialog = false },
      title = { Text("Отладка кэша") },
      text = {
        Column {
          // Test data input
          OutlinedTextField(
            value = debugKey,
            onValueChange = { debugKey = it },
            label = { Text("Ключ") },
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = debugValue,
            onValueChange = { debugValue = it },
            label = { Text("Значение") },
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                coroutineScope.launch {
                  viewModel.putTestData(debugKey, debugValue)
                  debugKey = ""
                  debugValue = ""
                }
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Добавить")
            }

            OutlinedButton(
              onClick = {
                coroutineScope.launch {
                  viewModel.getCacheEntryDebug(debugKey)?.let { value ->
                    debugValue = value
                  }
                }
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Получить")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDebugDialog = false }) {
          Text("Закрыть")
        }
      },
    )
  }
}
