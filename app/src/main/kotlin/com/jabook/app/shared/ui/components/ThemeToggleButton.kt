package com.jabook.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jabook.app.shared.ui.AppThemeMode

/**
 * Returns dynamic vertical padding based on screen height.
 * Minimum 12dp, maximum 32dp, optimal for different diagonals.
 */
@Composable
fun getDynamicVerticalPadding(): Dp {
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    return when {
        screenHeightDp < 600 -> 12.dp
        screenHeightDp < 800 -> 16.dp
        screenHeightDp < 1000 -> 20.dp
        else -> 32.dp
    }
}

@Composable
fun ThemeToggleButton(
    themeMode: AppThemeMode,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        when (themeMode) {
            AppThemeMode.SYSTEM ->
                Icon(
                    imageVector = Icons.Filled.SettingsBrightness,
                    contentDescription = "System theme",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            AppThemeMode.LIGHT ->
                Icon(
                    imageVector = Icons.Filled.Brightness7,
                    contentDescription = "Light theme",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            AppThemeMode.DARK ->
                Icon(
                    imageVector = Icons.Filled.Brightness4,
                    contentDescription = "Dark theme",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
        }
    }
}
