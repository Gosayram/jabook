package com.jabook.app.jabook.ui.theme

import androidx.compose.ui.graphics.Color

// ====================
// BETA THEME COLORS (Green theme)
// ====================
// Background: Olive-green, soft "warm beta-background"
val beta_light_background = Color(0xFFB6C36A)
val beta_light_onBackground = Color(0xFF1F3E5A) // Dark blue text on green

// Primary: Dark blue with cool tone (book icon, main color)
val beta_light_primary = Color(0xFF1F3E5A)
val beta_light_onPrimary = Color(0xFFF3E7CF) // Milky-cream text on blue

// Surface: Use light cream for cards/surfaces
val beta_light_surface = Color(0xFFF3E7CF)
val beta_light_onSurface = Color(0xFF1F3E5A)

// Secondary: Red for Beta badge
val beta_light_secondary = Color(0xFFE6453A)
val beta_light_onSecondary = Color(0xFFFFFFFF)

// Container colors
val beta_light_primaryContainer = Color(0xFF2A5A7F) // Slightly lighter blue
val beta_light_onPrimaryContainer = Color(0xFFF3E7CF)
val beta_light_secondaryContainer = Color(0xFFFF6B5E)
val beta_light_onSecondaryContainer = Color(0xFFFFFFFF)

// Other colors
val beta_light_tertiary = Color(0xFFB6C36A)
val beta_light_onTertiary = Color(0xFF1F3E5A)
val beta_light_error = Color(0xFFBA1A1A)
val beta_light_onError = Color(0xFFFFFFFF)
val beta_light_outline = Color(0xFF7A8F6A)
val beta_light_surfaceVariant = Color(0xFFE8E3D8)
val beta_light_onSurfaceVariant = Color(0xFF4A4E42)

// Dark theme for beta (if needed - inverse colors)
val beta_dark_background = Color(0xFF1F3E5A)
val beta_dark_onBackground = Color(0xFFF3E7CF)
val beta_dark_primary = Color(0xFFB6C36A)
val beta_dark_onPrimary = Color(0xFF1F3E5A)
val beta_dark_surface = Color(0xFF2A3E52)
val beta_dark_onSurface = Color(0xFFF3E7CF)
val beta_dark_secondary = Color(0xFFE6453A)
val beta_dark_onSecondary = Color(0xFFFFFFFF)

// ====================
// PROD THEME COLORS (Purple theme)
// ====================
// Background: Deep dark purple with gradient
val prod_light_background = Color(0xFF3B2452)
val prod_light_onBackground = Color(0xFFF6EAD7) // Creamy-white text on purple

// Primary: Saturated warm orange-yellow (book icon, accent)
val prod_light_primary = Color(0xFFF5A623)
val prod_light_onPrimary = Color(0xFF3B2452) // Purple text on orange

// Surface: Use cream for cards/surfaces
val prod_light_surface = Color(0xFFF6EAD7)
val prod_light_onSurface = Color(0xFF3B2452)

// Secondary: Keep purple theme consistent
val prod_light_secondary = Color(0xFF6B4C87)
val prod_light_onSecondary = Color(0xFFF6EAD7)

// Container colors
val prod_light_primaryContainer = Color(0xFFFFB84D) // Lighter orange
val prod_light_onPrimaryContainer = Color(0xFF3B2452)
val prod_light_secondaryContainer = Color(0xFF8B6CA8)
val prod_light_onSecondaryContainer = Color(0xFFF6EAD7)

// Other colors
val prod_light_tertiary = Color(0xFFF5A623)
val prod_light_onTertiary = Color(0xFF3B2452)
val prod_light_error = Color(0xFFBA1A1A)
val prod_light_onError = Color(0xFFFFFFFF)
val prod_light_outline = Color(0xFF8B7A9B)
val prod_light_surfaceVariant = Color(0xFFEBE1D7)
val prod_light_onSurfaceVariant = Color(0xFF4E4452)

// Dark theme for prod (lighter purple background)
val prod_dark_background = Color(0xFF2B1A3A)
val prod_dark_onBackground = Color(0xFFF6EAD7)
val prod_dark_primary = Color(0xFFF5A623)
val prod_dark_onPrimary = Color(0xFF3B2452)
val prod_dark_surface = Color(0xFF3B2452)
val prod_dark_onSurface = Color(0xFFF6EAD7)
val prod_dark_secondary = Color(0xFF8B6CA8)
val prod_dark_onSecondary = Color(0xFFF6EAD7)

// ====================
// DEV/STAGE THEME (Use standard Material colors or beta theme)
// ====================
// For dev and stage, we can reuse beta colors or use standard Material theme
// Using beta colors as default for dev/stage
