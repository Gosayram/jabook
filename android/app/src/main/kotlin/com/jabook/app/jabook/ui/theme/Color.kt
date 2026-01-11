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
// NEUTRAL COLORS (Based on Material 3 Guidelines)
// Used for backgrounds & surfaces across all themes
// ====================

public object NeutralColors {
    // Light theme neutrals
    public val Neutral50 = Color(0xFFFAFAFA) // Nearly white - light background
    public val Neutral100 = Color(0xFFF5F5F5) // Light gray - light surface
    public val Neutral200 = Color(0xFFE5E5E5) // Light gray - light surfaceVariant

    // Dark theme neutrals
    public val Neutral800 = Color(0xFF262626) // Dark gray - dark surfaceVariant
    public val Neutral900 = Color(0xFF171717) // Nearly black - dark background/surface
}

// ====================
// BETA THEME COLORS (Cyber-Premium Tech)
// Concept: Neon Electric Green primary
// ====================

// Primary accent color for Beta
val BetaPrimaryColor = Color(0xFF39FF14) // Electric Lime / Neon Green

// Beta Light Theme
val beta_light_background = NeutralColors.Neutral50
val beta_light_onBackground = Color(0xFF1A1C1E)
val beta_light_surface = NeutralColors.Neutral100
val beta_light_onSurface = Color(0xFF1A1C1E)
val beta_light_surfaceVariant = NeutralColors.Neutral200
val beta_light_onSurfaceVariant = Color(0xFF44474E)
val beta_light_primary = BetaPrimaryColor
val beta_light_onPrimary = Color(0xFF003300) // Dark green text on neon
val beta_light_primaryContainer = Color(0xFFB8F5A2)
val beta_light_onPrimaryContainer = Color(0xFF002200)
val beta_light_secondary = Color(0xFF00B4D8) // Electric Blue
val beta_light_onSecondary = Color(0xFFFFFFFF)
val beta_light_secondaryContainer = Color(0xFFCAE6FF)
val beta_light_onSecondaryContainer = Color(0xFF001E30)
val beta_light_tertiary = Color(0xFF00F0FF) // Cyan accent
val beta_light_onTertiary = Color(0xFF003333)
val beta_light_error = Color(0xFFBA1A1A)
val beta_light_onError = Color(0xFFFFFFFF)
val beta_light_outline = Color(0xFF74777F)

// Beta Dark Theme
val beta_dark_background = NeutralColors.Neutral900
val beta_dark_onBackground = Color(0xFFE2E2E6)
val beta_dark_surface = NeutralColors.Neutral900
val beta_dark_onSurface = Color(0xFFE2E2E6)
val beta_dark_surfaceVariant = NeutralColors.Neutral800
val beta_dark_onSurfaceVariant = Color(0xFFC4C6CF)
val beta_dark_primary = BetaPrimaryColor
val beta_dark_onPrimary = Color(0xFF003300)
val beta_dark_primaryContainer = Color(0xFF004D00)
val beta_dark_onPrimaryContainer = Color(0xFFB8F5A2)
val beta_dark_secondary = Color(0xFF00B4D8)
val beta_dark_onSecondary = Color(0xFF00344D)
val beta_dark_secondaryContainer = Color(0xFF004D6E)
val beta_dark_onSecondaryContainer = Color(0xFFCAE6FF)
val beta_dark_tertiary = Color(0xFF00F0FF)
val beta_dark_onTertiary = Color(0xFF003333)
val beta_dark_error = Color(0xFFFFB4AB)
val beta_dark_onError = Color(0xFF690005)
val beta_dark_outline = Color(0xFF8E9099)

// ====================
// PROD THEME COLORS (Royal Premium)
// Concept: Deep Purple + Luxury Gold
// ====================

// Primary accent color for Prod
val ProdPrimaryColor = Color(0xFFFFD700) // Luxury Gold / Amber

// Prod Light Theme
val prod_light_background = NeutralColors.Neutral50
val prod_light_onBackground = Color(0xFF1C1B1F)
val prod_light_surface = NeutralColors.Neutral100
val prod_light_onSurface = Color(0xFF1C1B1F)
val prod_light_surfaceVariant = NeutralColors.Neutral200
val prod_light_onSurfaceVariant = Color(0xFF49454E)
val prod_light_primary = Color(0xFF7B5800) // Darker gold for light theme
val prod_light_onPrimary = Color(0xFFFFFFFF)
val prod_light_primaryContainer = Color(0xFFFFDEA6)
val prod_light_onPrimaryContainer = Color(0xFF271900)
val prod_light_secondary = Color(0xFF6750A4) // Royal Purple
val prod_light_onSecondary = Color(0xFFFFFFFF)
val prod_light_secondaryContainer = Color(0xFFE9DDFF)
val prod_light_onSecondaryContainer = Color(0xFF22005D)
val prod_light_tertiary = Color(0xFF7B5800)
val prod_light_onTertiary = Color(0xFFFFFFFF)
val prod_light_error = Color(0xFFBA1A1A)
val prod_light_onError = Color(0xFFFFFFFF)
val prod_light_outline = Color(0xFF7A757F)

// Prod Dark Theme
val prod_dark_background = NeutralColors.Neutral900
val prod_dark_onBackground = Color(0xFFE6E1E6)
val prod_dark_surface = NeutralColors.Neutral900
val prod_dark_onSurface = Color(0xFFE6E1E6)
val prod_dark_surfaceVariant = NeutralColors.Neutral800
val prod_dark_onSurfaceVariant = Color(0xFFCAC4CF)
val prod_dark_primary = ProdPrimaryColor
val prod_dark_onPrimary = Color(0xFF402D00)
val prod_dark_primaryContainer = Color(0xFF5C4200)
val prod_dark_onPrimaryContainer = Color(0xFFFFDEA6)
val prod_dark_secondary = Color(0xFFCFBCFF) // Light Purple
val prod_dark_onSecondary = Color(0xFF381E72)
val prod_dark_secondaryContainer = Color(0xFF4F378A)
val prod_dark_onSecondaryContainer = Color(0xFFE9DDFF)
val prod_dark_tertiary = ProdPrimaryColor
val prod_dark_onTertiary = Color(0xFF402D00)
val prod_dark_error = Color(0xFFFFB4AB)
val prod_dark_onError = Color(0xFF690005)
val prod_dark_outline = Color(0xFF948F99)
