package com.jabook.app.features.settings.presentation.components

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel

@Composable
fun TestLogEntrySection(
    viewModel: RuTrackerSettingsViewModel,
) {
    val context = LocalContext.current

    // Button to manually write a test log entry for SAF diagnostics
    Button(
        onClick = {
            try {
                viewModel.writeTestLogEntry()
                Toast.makeText(context, context.getString(R.string.test_log_written), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.write_test_log_failed, e.message), Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.write_test_log_entry))
    }
}
