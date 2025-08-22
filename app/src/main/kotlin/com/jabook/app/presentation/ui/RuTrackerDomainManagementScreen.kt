package com.jabook.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jabook.app.core.network.domain.DomainHealthStatus
import com.jabook.app.core.network.errorhandler.ErrorReport
import com.jabook.app.core.network.errorhandler.ErrorSeverity
import com.jabook.app.presentation.viewmodel.RuTrackerDomainViewModel

/**
 * Domain status card component
 */
@Composable
fun DomainStatusCard(
  domain: String,
  status: DomainHealthStatus,
  isCurrent: Boolean,
  onSwitchDomain: (String) -> Unit,
  onToggleActive: (String, Boolean) -> Unit,
  onResetCircuitBreaker: (String) -> Unit,
  onViewDetails: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isActive = status.isAvailable
  val circuitBreakerState = status.circuitBreakerState

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surface
          },
      ),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = domain,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )

            if (isCurrent) {
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Current domain",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (isActive) Icons.Default.Wifi else Icons.Default.WifiOff,
              contentDescription = if (isActive) "Available" else "Unavailable",
              tint = if (isActive) Color.Green else Color.Red,
              modifier = Modifier.size(16.dp),
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
              text = if (isActive) "Доступен" else "Недоступен",
              style = MaterialTheme.typography.bodySmall,
              color = if (isActive) Color.Green else Color.Red,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
              text = "${status.responseTime}ms",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Circuit Breaker: $circuitBreakerState",
            style = MaterialTheme.typography.bodySmall,
            color =
              when (circuitBreakerState) {
                "CLOSED" -> Color.Green
                "OPEN" -> Color.Red
                "HALF_OPEN" -> Color.Orange
                else -> Color.Gray
              },
          )

          if (status.consecutiveFailures > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Ошибок подряд: ${status.consecutiveFailures}",
              style = MaterialTheme.typography.bodySmall,
              color = Color.Orange,
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.End,
        ) {
          // Switch domain button
          if (!isCurrent) {
            OutlinedButton(
              onClick = { onSwitchDomain(domain) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Выбрать")
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Toggle active button
          Switch(
            checked = isActive,
            onCheckedChange = { onToggleActive(domain, it) },
          )

          Spacer(modifier = Modifier.height(4.dp))

          // Reset circuit breaker button
          if (circuitBreakerState == "OPEN") {
            OutlinedButton(
              onClick = { onResetCircuitBreaker(domain) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Сбросить CB")
            }
          }

          // View details button
          TextButton(
            onClick = { onViewDetails(domain) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Детали")
          }
        }
      }
    }
  }
}

/**
 * Error report card component
 */
@Composable
fun ErrorReportCard(
  errorReport: ErrorReport,
  onMarkResolved: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (errorReport.isResolved) {
            MaterialTheme.colorScheme.surfaceVariant
          } else {
            MaterialTheme.colorScheme.errorContainer
          },
      ),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = errorReport.exception::class.simpleName ?: "Unknown Error",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = errorReport.userMessage,
            style = MaterialTheme.typography.bodyMedium,
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Операция: ${errorReport.context.operation}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          errorReport.context.domain?.let { domain ->
            Text(
              text = "Домен: $domain",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.End,
        ) {
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                  when (errorReport.severity) {
                    ErrorSeverity.CRITICAL -> Color.Red
                    ErrorSeverity.HIGH -> Color.Orange
                    ErrorSeverity.MEDIUM -> Color.Yellow
                    ErrorSeverity.LOW -> Color.Green
                  },
                ).padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text(
              text = errorReport.severity.name,
              style = MaterialTheme.typography.bodySmall,
              color = Color.White,
            )
          }

          if (!errorReport.isResolved) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = { onMarkResolved(errorReport.id) },
            ) {
              Text("Решить")
            }
          }
        }
      }
    }
  }
}

/**
 * Main domain management screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuTrackerDomainManagementScreen(
  viewModel: RuTrackerDomainViewModel = hiltViewModel(),
  onNavigateBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  var showErrors by remember { mutableStateOf(false) }
  var showDetailsDialog by remember { mutableStateOf(false) }
  var selectedDomain by remember { mutableStateOf("") }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Управление доменами RuTracker") },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { viewModel.refreshDomainStatuses() }) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = if (uiState.isRefreshing) MaterialTheme.colorScheme.primary else LocalContentColor.current,
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
      // Summary section
      DomainHealthSummary(
        summary = viewModel.getDomainHealthSummary(),
        lastUpdated = viewModel.getFormattedLastUpdated(),
        onRefresh = { viewModel.refreshDomainStatuses() },
        isRefreshing = uiState.isRefreshing,
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Tab selection
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !showErrors,
          onClick = { showErrors = false },
          label = { Text("Домены") },
          leadingIcon =
            if (!showErrors) {
              { Icon(Icons.Default.Dns, contentDescription = null) }
            } else {
              null
            },
        )

        FilterChip(
          selected = showErrors,
          onClick = { showErrors = true },
          label = { Text("Ошибки") },
          leadingIcon =
            if (showErrors) {
              { Icon(Icons.Default.Error, contentDescription = null) }
            } else {
              null
            },
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Content based on tab selection
      if (showErrors) {
        ErrorReportsContent(
          errorReports = uiState.errorReports,
          errorSummary = uiState.errorSummary,
          onMarkResolved = { viewModel.markErrorAsResolved(it) },
          onClearAll = { viewModel.clearAllErrors() },
          onClearResolved = { viewModel.clearResolvedErrors() },
        )
      } else {
        DomainsContent(
          domainStatuses = uiState.domainStatuses,
          currentDomain = uiState.currentDomain,
          onSwitchDomain = { viewModel.switchDomain(it) },
          onToggleActive = { domain, active -> viewModel.setDomainActive(domain, active) },
          onResetCircuitBreaker = { viewModel.resetCircuitBreaker(it) },
          onViewDetails = {
            selectedDomain = it
            showDetailsDialog = true
          },
        )
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

  // Domain details dialog
  if (showDetailsDialog && selectedDomain.isNotEmpty()) {
    DomainDetailsDialog(
      domain = selectedDomain,
      statistics = viewModel.getDomainStatistics(),
      circuitBreakerStatus = viewModel.getCircuitBreakerStatus(selectedDomain),
      errors = viewModel.getErrorsByDomain(selectedDomain),
      onDismiss = {
        showDetailsDialog = false
        selectedDomain = ""
      },
    )
  }
}

/**
 * Domain health summary component
 */
@Composable
fun DomainHealthSummary(
  summary: Map<String, Int>,
  lastUpdated: String,
  onRefresh: () -> Unit,
  isRefreshing: Boolean,
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Состояние доменов",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Обновлено: $lastUpdated",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = if (isRefreshing) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        SummaryItem(
          label = "Всего",
          value = summary["total"] ?: 0,
          color = MaterialTheme.colorScheme.onSurface,
        )

        SummaryItem(
          label = "Доступны",
          value = summary["available"] ?: 0,
          color = Color.Green,
        )

        SummaryItem(
          label = "Предупреждение",
          value = summary["warning"] ?: 0,
          color = Color.Orange,
        )

        SummaryItem(
          label = "Критично",
          value = summary["critical"] ?: 0,
          color = Color.Red,
        )
      }
    }
  }
}

/**
 * Summary item component
 */
@Composable
fun SummaryItem(
  label: String,
  value: Int,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value.toString(),
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
 * Domains content component
 */
@Composable
fun DomainsContent(
  domainStatuses: Map<String, DomainHealthStatus>,
  currentDomain: String,
  onSwitchDomain: (String) -> Unit,
  onToggleActive: (String, Boolean) -> Unit,
  onResetCircuitBreaker: (String) -> Unit,
  onViewDetails: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
  ) {
    items(domainStatuses.keys.sorted()) { domain ->
      val status = domainStatuses[domain] ?: return@items
      DomainStatusCard(
        domain = domain,
        status = status,
        isCurrent = domain == currentDomain,
        onSwitchDomain = onSwitchDomain,
        onToggleActive = onToggleActive,
        onResetCircuitBreaker = onResetCircuitBreaker,
        onViewDetails = onViewDetails,
      )
    }
  }
}

/**
 * Error reports content component
 */
@Composable
fun ErrorReportsContent(
  errorReports: List<ErrorReport>,
  errorSummary: Map<String, String>,
  onMarkResolved: (String) -> Unit,
  onClearAll: () -> Unit,
  onClearResolved: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize(),
  ) {
    // Error summary
    Card(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(16.dp),
      ) {
        Text(
          text = "Статистика ошибок",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          errorSummary.forEach { (key, value) ->
            SummaryItem(
              label = key,
              value = value.toIntOrNull() ?: 0,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onClearResolved,
            modifier = Modifier.weight(1f),
          ) {
            Text("Очистить решенные")
          }

          OutlinedButton(
            onClick = onClearAll,
            modifier = Modifier.weight(1f),
          ) {
            Text("Очистить все")
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Error reports list
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
    ) {
      items(errorReports) { errorReport ->
        ErrorReportCard(
          errorReport = errorReport,
          onMarkResolved = onMarkResolved,
        )
      }
    }
  }
}

/**
 * Domain details dialog
 */
@Composable
fun DomainDetailsDialog(
  domain: String,
  statistics: Map<String, Map<String, Any>>,
  circuitBreakerStatus: String?,
  errors: List<ErrorReport>,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Детали домена: $domain") },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
      ) {
        item {
          // Circuit breaker status
          Card(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
            ) {
              Text(
                text = "Circuit Breaker",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = circuitBreakerStatus ?: "Недоступно",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }

        item {
          // Statistics
          val domainStats = statistics[domain]
          if (domainStats != null) {
            Card(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
              ) {
                Text(
                  text = "Статистика",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                domainStats.forEach { (key, value) ->
                  Text(
                    text = "$key: $value",
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
          }
        }

        // Errors
        if (errors.isNotEmpty()) {
          item {
            Text(
              text = "Последние ошибки (${errors.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }

          items(errors.take(5)) { error ->
            ErrorReportCard(
              errorReport = error,
              onMarkResolved = {},
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Закрыть")
      }
    },
  )
}
