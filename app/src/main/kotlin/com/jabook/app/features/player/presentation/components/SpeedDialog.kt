package com.jabook.app.features.player.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jabook.app.shared.ui.theme.JaBookAnimations

@Composable
fun SpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val availableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Playback Speed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(availableSpeeds) { speed ->
                    SpeedDialogItem(speed = speed, isSelected = speed == currentSpeed, onSpeedSelected = onSpeedSelected)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SpeedDialogItem(speed: Float, isSelected: Boolean, onSpeedSelected: (Float) -> Unit) {
    val scale by
        animateFloatAsState(
            targetValue = if (isSelected) 1.05f else 1.0f,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "speedItemScale",
        )

    val backgroundColor by
        animateColorAsState(
            targetValue =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "speedItemBackground",
        )

    val textColor by
        animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "speedItemTextColor",
        )

    Row(
        modifier =
        Modifier.fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onSpeedSelected(speed) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = isSelected, onClick = { onSpeedSelected(speed) })

        AnimatedVisibility(visible = true, enter = JaBookAnimations.dialogEnterTransition, exit = JaBookAnimations.dialogExitTransition) {
            Text(
                text = "${speed}x",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
