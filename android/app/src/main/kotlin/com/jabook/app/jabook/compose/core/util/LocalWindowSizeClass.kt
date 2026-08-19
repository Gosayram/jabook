package com.jabook.app.jabook.compose.core.util

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.staticCompositionLocalOf

public val LocalWindowSizeClass: androidx.compose.runtime.ProvidableCompositionLocal<WindowSizeClass?> =
    staticCompositionLocalOf<WindowSizeClass?> { null }
