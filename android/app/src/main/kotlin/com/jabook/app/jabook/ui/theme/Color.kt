// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.ui.theme

import androidx.compose.ui.graphics.Color

// ====================
// BETA THEME COLORS (Cyber-Premium Tech)
// Concept: Deep Midnight Navy + Neon Electric Green
// ====================

// Background: Deep Midnight Navy - Almost black, void-like
val beta_light_background = Color(0xFF050B14)
val beta_light_onBackground = Color(0xFFE0E6ED) // Crisp white-blue text

// Primary: Electric Lime / Neon Green - High energy accent
val beta_light_primary = Color(0xFF39FF14)
val beta_light_onPrimary = Color(0xFF030810) // Dark text on bright neon

// Surface: Deep Ocean - Slightly lighter than background, glass-like
val beta_light_surface = Color(0xFF0F1C2E)
val beta_light_onSurface = Color(0xFFE0E6ED)

// Secondary: Electric Blue - Secondary accent for active states
val beta_light_secondary = Color(0xFF00F0FF)
val beta_light_onSecondary = Color(0xFF030810)

// Container colors
val beta_light_primaryContainer = Color(0xFF132F18) // Dark green tint
val beta_light_onPrimaryContainer = Color(0xFF39FF14)
val beta_light_secondaryContainer = Color(0xFF0F2633)
val beta_light_onSecondaryContainer = Color(0xFF00F0FF)

// Other colors
val beta_light_tertiary = Color(0xFF00F0FF)
val beta_light_onTertiary = Color(0xFF030810)
val beta_light_error = Color(0xFFFF0055) // Neon Red
val beta_light_onError = Color(0xFFFFFFFF)
val beta_light_outline = Color(0xFF2C3E50)
val beta_light_surfaceVariant = Color(0xFF162438)
val beta_light_onSurfaceVariant = Color(0xFF8A9AB0)

// Dark theme for beta (Reuse same Cyber palette as it is inherently dark)
val beta_dark_background = beta_light_background
val beta_dark_onBackground = beta_light_onBackground
val beta_dark_primary = beta_light_primary
val beta_dark_onPrimary = beta_light_onPrimary
val beta_dark_surface = beta_light_surface
val beta_dark_onSurface = beta_light_onSurface
val beta_dark_secondary = beta_light_secondary
val beta_dark_onSecondary = beta_light_onSecondary

// ====================
// PROD THEME COLORS (Royal Premium)
// Concept: Deep Void Purple + Luxury Gold
// ====================

// Background: Deep Aubergine / Void Purple
val prod_light_background = Color(0xFF1A0B2E)
val prod_light_onBackground = Color(0xFFFBF5E9) // Luxury cream text

// Primary: Luxury Gold / Amber
val prod_light_primary = Color(0xFFFFD700)
val prod_light_onPrimary = Color(0xFF1A0B2E)

// Surface: Darkened Glass / Deep Grape
val prod_light_surface = Color(0xFF2A1B3D)
val prod_light_onSurface = Color(0xFFFBF5E9)

// Secondary: Royal Lavender
val prod_light_secondary = Color(0xFF9D65C9)
val prod_light_onSecondary = Color(0xFFFFFFFF)

// Container colors
val prod_light_primaryContainer = Color(0xFF4A3418)
val prod_light_onPrimaryContainer = Color(0xFFFFD700)
val prod_light_secondaryContainer = Color(0xFF3F2755)
val prod_light_onSecondaryContainer = Color(0xFFE6D6F5)

// Other colors
val prod_light_tertiary = Color(0xFFFFD700)
val prod_light_onTertiary = Color(0xFF1A0B2E)
val prod_light_error = Color(0xFFCF6679)
val prod_light_onError = Color(0xFF1A0B2E)
val prod_light_outline = Color(0xFF4D3B63)
val prod_light_surfaceVariant = Color(0xFF35244A)
val prod_light_onSurfaceVariant = Color(0xFFCEC0DE)

// Dark theme for prod (Reuse same Royal palette)
val prod_dark_background = prod_light_background
val prod_dark_onBackground = prod_light_onBackground
val prod_dark_primary = prod_light_primary
val prod_dark_onPrimary = prod_light_onPrimary
val prod_dark_surface = prod_light_surface
val prod_dark_onSurface = prod_light_onSurface
val prod_dark_secondary = prod_light_secondary
val prod_dark_onSecondary = prod_light_onSecondary

// ====================
// DEV/STAGE THEME (Fallback to Beta)
// ====================
// For dev and stage, we reuse the Beta (Cyber) theme
