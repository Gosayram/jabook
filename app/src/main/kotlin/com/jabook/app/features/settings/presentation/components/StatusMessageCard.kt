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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun StatusMessageCard(
  modifier: Modifier = Modifier,
  errorMessage: String? = null,
  successMessage: String? = null,
  onDismiss: (() -> Unit)? = null,      // опциональная кнопка закрытия
  showIcon: Boolean = true,             // можно скрыть иконку при желании
) {
  // Приоритет: ошибка > успех
  val isError = errorMessage != null
  val message = errorMessage ?: successMessage

  AnimatedVisibility(
    visible = message != null,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
  ) {
    if (message == null) return@AnimatedVisibility

    val (container, content, icon, iconTint) =
      if (isError) {
        arrayOf(
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
          Icons.Filled.Error,
          MaterialTheme.colorScheme.onErrorContainer,
        )
      } else {
        arrayOf(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          Icons.Filled.CheckCircle,
          MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

    Card(
      modifier = modifier
        .fillMaxWidth()
        // озвучиваем как "живой" регион — экран-ридеры сразу проговорят обновление
        .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
      colors = CardDefaults.cardColors(containerColor = container),
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
            imageVector = if (isError) icon as Icons.Filled.Error else Icons.Filled.Info, // запасная инфо-иконка
            contentDescription = null,
            tint = iconTint as androidx.compose.ui.graphics.Color,
            modifier = Modifier.size(20.dp),
          )
        }

        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = content as androidx.compose.ui.graphics.Color,
          modifier = Modifier.weight(1f),
        )

        if (onDismiss != null) {
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Dismiss",
              tint = content,
            )
          }
        }
      }
    }
  }
}
