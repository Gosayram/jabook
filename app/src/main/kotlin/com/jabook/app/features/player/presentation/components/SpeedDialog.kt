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
fun SpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val availableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Playback Speed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(availableSpeeds) { speed ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        RadioButton(selected = speed == currentSpeed, onClick = { onSpeedSelected(speed) })
                        Text(text = "${speed}x", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
