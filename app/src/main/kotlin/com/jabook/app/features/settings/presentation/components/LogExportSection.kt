package com.jabook.app.features.settings.presentation.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel
import java.io.File

@Composable
fun LogExportSection(viewModel: RuTrackerSettingsViewModel) {
    val context = LocalContext.current

    Button(
        onClick = {
            // Export logs and share via system intent
            val logFile: File? = viewModel.exportLogs()
            if (logFile != null && logFile.exists()) {
                try {
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            logFile,
                        )
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, "Share debug log file"))
                    Toast.makeText(context, context.getString(R.string.export_logs_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.export_logs_failed, e.message), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.log_file_not_found), Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.export_debug_logs))
    }
}
