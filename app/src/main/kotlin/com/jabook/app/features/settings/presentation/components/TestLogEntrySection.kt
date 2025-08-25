package com.jabook.app.features.settings.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel

@Composable
fun TestLogEntrySection(viewModel: RuTrackerSettingsViewModel) {
  val context = LocalContext.current
  var isWriting by remember { mutableStateOf(false) }

  Button(
    onClick = {
      if (isWriting) return@Button
      isWriting = true
      try {
        viewModel.writeTestLogEntry()
        Toast.makeText(
          context,
          context.getString(R.string.test_log_written),
          Toast.LENGTH_SHORT
        ).show()
      } catch (e: Exception) {
        Toast.makeText(
          context,
          context.getString(R.string.write_test_log_failed, e.message ?: "Error"),
          Toast.LENGTH_LONG
        ).show()
      } finally {
        isWriting = false
      }
    },
    modifier = Modifier.fillMaxWidth(),
    enabled = !isWriting,
  ) {
    if (isWriting) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    } else {
      Text(text = stringResource(R.string.write_test_log_entry))
    }
  }
}
