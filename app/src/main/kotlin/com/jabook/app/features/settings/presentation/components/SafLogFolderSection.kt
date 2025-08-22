package com.jabook.app.features.settings.presentation.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel

@Composable
fun SafLogFolderSection(viewModel: RuTrackerSettingsViewModel) {
  val context = LocalContext.current

  // State to force recomposition when SAF Uri changes
  var safUriState by remember { mutableStateOf(viewModel.getLogFolderUri()) }

  // Get SAF log folder Uri from ViewModel/SharedPreferences
  val logFolderUriString = safUriState
  val logFileName = "debug_log.txt"
  // Show log file path or SAF Uri
  if (logFolderUriString != null) {
    Text(
      text = stringResource(R.string.log_folder_saf, logFolderUriString, logFileName),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    // Show a hint if log file is not found
    if (viewModel.getLogFileUriFromSaf(logFileName) == null) {
      Text(
        text = stringResource(R.string.log_file_will_appear),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(bottom = 4.dp),
      )
    }
  } else {
    val logFile = viewModel.exportLogs()
    val logFilePath = logFile?.absolutePath ?: stringResource(R.string.log_file_not_found)
    Text(
      text = stringResource(R.string.log_file_path, logFilePath),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
  }

  // SAF launcher for selecting log folder
  val logFolderLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        // Pass the selected folder Uri to the ViewModel or save in preferences
        viewModel.setLogFolderUri(uri.toString())
        safUriState = uri.toString() // Force recomposition
        Toast.makeText(context, context.getString(R.string.log_folder_selected, uri), Toast.LENGTH_SHORT).show()
        android.util.Log.d("RuTrackerSettingsScreen", "SAF Uri saved: $uri")
        // Write a test log entry to create the file immediately
        viewModel.writeTestLogEntry()
      }
    }

  // Button to select log folder via SAF
  Button(
    onClick = {
      logFolderLauncher.launch(null)
    },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(stringResource(R.string.select_log_folder))
  }
}
