package com.jabook.app.features.settings.presentation.components

import android.content.ActivityNotFoundException
import android.content.ClipData
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
import com.jabook.app.BuildConfig
import com.jabook.app.R
import com.jabook.app.features.settings.presentation.RuTrackerSettingsViewModel
import java.io.File

@Composable
fun LogExportSection(viewModel: RuTrackerSettingsViewModel) {
  val context = LocalContext.current

  Button(
    onClick = {
      val logFile: File? = viewModel.exportLogs()
      if (logFile != null && logFile.exists()) {
        try {
          val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider", // == authority в манифесте
            logFile
          )

          val mime = context.contentResolver.getType(uri) ?: "text/plain"

          val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_debug_logs))
            clipData = ClipData.newRawUri("logs", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }

          val chooserTitle = stringResource(R.string.share_debug_log_file)
          val chooser = Intent.createChooser(shareIntent, chooserTitle)

          try {
            context.startActivity(chooser)
            Toast.makeText(
              context,
              context.getString(R.string.export_logs_success),
              Toast.LENGTH_SHORT
            ).show()
          } catch (anf: ActivityNotFoundException) {
            Toast.makeText(
              context,
              context.getString(R.string.no_apps_to_share),
              Toast.LENGTH_LONG
            ).show()
          }
        } catch (e: Exception) {
          Toast.makeText(
            context,
            context.getString(R.string.export_logs_failed, e.message ?: "Error"),
            Toast.LENGTH_LONG
          ).show()
        }
      } else {
        Toast.makeText(
          context,
          context.getString(R.string.log_file_not_found),
          Toast.LENGTH_SHORT
        ).show()
      }
    },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(text = stringResource(R.string.export_debug_logs))
  }
}
