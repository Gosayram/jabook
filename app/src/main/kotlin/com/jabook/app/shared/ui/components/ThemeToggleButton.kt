package com.jabook.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.jabook.app.shared.ui.AppThemeMode

@Composable
fun ThemeToggleButton(themeMode: AppThemeMode, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        when (themeMode) {
            AppThemeMode.SYSTEM -> Icon(
                imageVector = Icons.Filled.SettingsBrightness,
                contentDescription = "System theme",
                tint = MaterialTheme.colorScheme.onSurface
            )
            AppThemeMode.LIGHT -> Icon(
                imageVector = Icons.Filled.Brightness7,
                contentDescription = "Light theme",
                tint = MaterialTheme.colorScheme.onSurface
            )
            AppThemeMode.DARK -> Icon(
                imageVector = Icons.Filled.Brightness4,
                contentDescription = "Dark theme",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
