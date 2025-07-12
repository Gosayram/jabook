package com.jabook.app.features.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SleepTimerDialog(currentMinutes: Int, onTimerSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val availableTimers = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120) // 0 means off

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Sleep Timer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(availableTimers) { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        RadioButton(selected = minutes == currentMinutes, onClick = { onTimerSet(minutes) })
                        Text(
                            text = if (minutes == 0) "Off" else "$minutes minutes",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
