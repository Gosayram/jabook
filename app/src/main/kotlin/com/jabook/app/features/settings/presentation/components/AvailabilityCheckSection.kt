package com.jabook.app.features.settings.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel

@Composable
fun AvailabilityCheckSection(viewModel: RuTrackerSettingsViewModel) {
  // Button to manually check RuTracker availability
  Button(
    onClick = {
      viewModel.checkRuTrackerAvailability()
    },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(stringResource(R.string.rutracker_check_availability))
  }
}
