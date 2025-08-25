package com.jabook.app.features.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jabook.app.R

@Composable
fun ModeToggleCard(
  isGuestMode: Boolean,
  onModeChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val modeTitle = stringResource(R.string.ru_tracker_mode)
  val guestOn = stringResource(R.string.guest_mode_on)
  val guestOff = stringResource(R.string.guest_mode_off)
  val descText = if (isGuestMode) {
    stringResource(R.string.guest_mode_desc)
  } else {
    stringResource(R.string.authorized_mode_desc)
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .semantics {
          contentDescription = modeTitle
          stateDescription = if (isGuestMode) guestOn else guestOff
        },
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = modeTitle,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = descText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = if (isGuestMode) stringResource(R.string.guest_mode) else stringResource(R.string.authorized_mode),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
          checked = isGuestMode, // прямое соответствие: true = гость
          onCheckedChange = onModeChange,
          modifier = Modifier.semantics { this.role = Role.Switch },
        )
      }
    }
  }
}
