package com.jabook.app.features.settings.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel

@Composable
fun AvailabilityCheckSection(viewModel: RuTrackerSettingsViewModel) {
  val state = viewModel.state.collectAsState().value

  Button(
    onClick = { viewModel.checkRuTrackerAvailability() },
    modifier = Modifier.fillMaxWidth(),
    enabled = !state.isLoading,
  ) {
    if (state.isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    } else {
      Text(text = stringResource(R.string.rutracker_check_availability))
    }
  }
}
