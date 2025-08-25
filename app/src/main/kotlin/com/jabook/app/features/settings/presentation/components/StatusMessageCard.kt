package com.jabook.app.features.settings.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun StatusMessageCard(
  modifier: Modifier = Modifier,
  errorMessage: String? = null,
  successMessage: String? = null,
  onDismiss: (() -> Unit)? = null,   // опциональная кнопка закрытия
  showIcon: Boolean = true,          // можно скрыть иконку при желании
) {
  val isError = errorMessage != null
  val message = errorMessage ?: successMessage

  AnimatedVisibility(
    visible = message != null,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
  ) {
    if (message == null) return@AnimatedVisibility

    // Строго типизированные значения
    val containerColor: Color
    val contentColor: Color
    val leadingIcon =
      if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle

    if (isError) {
      containerColor = MaterialTheme.colorScheme.errorContainer
      contentColor = MaterialTheme.colorScheme.onErrorContainer
    } else {
      containerColor = MaterialTheme.colorScheme.primaryContainer
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
      modifier = modifier
        .fillMaxWidth()
        .semantics { liveRegion = LiveRegionMode.Polite }, // экран-ридеры озвучат обновление
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
      colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (showIcon) {
          Icon(
            imageVector = if (isError) leadingIcon else Icons.Filled.Info, // для успеха можно оставить Info, если так задумано
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
          )
        }

        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = contentColor,
          modifier = Modifier.weight(1f),
        )

        if (onDismiss != null) {
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Dismiss",
              tint = contentColor,
            )
          }
        }
      }
    }
  }
}
