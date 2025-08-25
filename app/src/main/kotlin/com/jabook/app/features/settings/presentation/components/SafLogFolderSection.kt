package com.jabook.app.features.settings.presentation.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

  // Состояние для отображения выбранной через SAF папки
  var safUriState by remember { mutableStateOf(viewModel.getLogFolderUri()) }

  // Текущая папка и имя файла логов
  val logFolderUriString = safUriState
  val logFileName = "debug_log.txt"

  // Текстовое описание текущего состояния
  Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
    if (logFolderUriString != null) {
      Text(
        text = stringResource(R.string.log_folder_saf, logFolderUriString, logFileName),
        style = MaterialTheme.typography.bodySmall,
      )
      // Подсказка, что файл появится после первой записи
      if (viewModel.getLogFileUriFromSaf(logFileName) == null) {
        Text(
          text = stringResource(R.string.log_file_will_appear),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    } else {
      // SAF не выбран — показываем внутренний путь приложения (filesDir/logs)
      val internalLogsHint = context.filesDir.resolve("logs").absolutePath
      Text(
        text = stringResource(R.string.log_file_path, "$internalLogsHint/$logFileName"),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }

  // SAF launcher для выбора папки
  val logFolderLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        try {
          // Сохраняем постоянные разрешения на выбранную папку
          val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          context.contentResolver.takePersistableUriPermission(uri, flags)

          // Сохраняем URI в VM/SharedPreferences
          viewModel.setLogFolderUri(uri.toString())
          safUriState = uri.toString()

          Toast.makeText(
            context,
            context.getString(R.string.log_folder_selected, uri),
            Toast.LENGTH_SHORT,
          ).show()

          // Создадим тестовую запись, чтобы файл появился сразу
          viewModel.writeTestLogEntry()
        } catch (e: SecurityException) {
          Toast.makeText(
            context,
            context.getString(R.string.log_folder_permission_failed, e.message ?: ""),
            Toast.LENGTH_LONG,
          ).show()
        } catch (e: Exception) {
          Toast.makeText(
            context,
            context.getString(R.string.export_logs_failed, e.message ?: ""),
            Toast.LENGTH_LONG,
          ).show()
        }
      }
    }

  // Кнопки действий
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Button(
      onClick = { logFolderLauncher.launch(null) },
      modifier = Modifier.weight(1f),
    ) {
      Text(stringResource(R.string.select_log_folder))
    }

    // Показываем "Сбросить" только если SAF уже выбран
    if (logFolderUriString != null) {
      OutlinedButton(
        onClick = {
          // Сбросить SAF: убираем сохранённый URI
          viewModel.setLogFolderUri("")
          safUriState = null
          Toast.makeText(
            context,
            context.getString(R.string.log_folder_reset_done),
            Toast.LENGTH_SHORT,
          ).show()
        },
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(R.string.reset_log_folder))
      }
    }
  }
}
