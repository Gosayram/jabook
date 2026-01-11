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
    public val Neutral50: Color = Color(0xFFFAFAFA) // Nearly white - light background
    public val Neutral100: Color = Color(0xFFF5F5F5) // Light gray - light surface
    public val Neutral200: Color = Color(0xFFE5E5E5) // Light gray - light surfaceVariant

    // Dark theme neutrals
    public val Neutral800: Color = Color(0xFF262626) // Dark gray - dark surfaceVariant
    public val Neutral900: Color = Color(0xFF171717) // Nearly black - dark background/surface
}

// ====================
// BETA THEME COLORS (Cyber-Premium Tech)
// Concept: Neon Electric Green primary
// ====================

// Primary accent color for Beta
public val BetaPrimaryColor: Color = Color(0xFF39FF14) // Electric Lime / Neon Green

// Beta Light Theme
public val beta_light_background: Color = NeutralColors.Neutral50
public val beta_light_onBackground: Color = Color(0xFF1A1C1E)
public val beta_light_surface: Color = NeutralColors.Neutral100
public val beta_light_onSurface: Color = Color(0xFF1A1C1E)
public val beta_light_surfaceVariant: Color = NeutralColors.Neutral200
public val beta_light_onSurfaceVariant: Color = Color(0xFF44474E)
public val beta_light_primary: Color = BetaPrimaryColor
public val beta_light_onPrimary: Color = Color(0xFF003300) // Dark green text on neon
public val beta_light_primaryContainer: Color = Color(0xFFB8F5A2)
public val beta_light_onPrimaryContainer: Color = Color(0xFF002200)
public val beta_light_secondary: Color = Color(0xFF00B4D8) // Electric Blue
public val beta_light_onSecondary: Color = Color(0xFFFFFFFF)
public val beta_light_secondaryContainer: Color = Color(0xFFCAE6FF)
public val beta_light_onSecondaryContainer: Color = Color(0xFF001E30)
public val beta_light_tertiary: Color = Color(0xFF00F0FF) // Cyan accent
public val beta_light_onTertiary: Color = Color(0xFF003333)
public val beta_light_error: Color = Color(0xFFBA1A1A)
public val beta_light_onError: Color = Color(0xFFFFFFFF)
public val beta_light_outline: Color = Color(0xFF74777F)

// Beta Dark Theme
public val beta_dark_background: Color = NeutralColors.Neutral900
public val beta_dark_onBackground: Color = Color(0xFFE2E2E6)
public val beta_dark_surface: Color = NeutralColors.Neutral900
public val beta_dark_onSurface: Color = Color(0xFFE2E2E6)
public val beta_dark_surfaceVariant: Color = NeutralColors.Neutral800
public val beta_dark_onSurfaceVariant: Color = Color(0xFFC4C6CF)
public val beta_dark_primary: Color = BetaPrimaryColor
public val beta_dark_onPrimary: Color = Color(0xFF003300)
public val beta_dark_primaryContainer: Color = Color(0xFF004D00)
public val beta_dark_onPrimaryContainer: Color = Color(0xFFB8F5A2)
public val beta_dark_secondary: Color = Color(0xFF00B4D8)
public val beta_dark_onSecondary: Color = Color(0xFF00344D)
public val beta_dark_secondaryContainer: Color = Color(0xFF004D6E)
public val beta_dark_onSecondaryContainer: Color = Color(0xFFCAE6FF)
public val beta_dark_tertiary: Color = Color(0xFF00F0FF)
public val beta_dark_onTertiary: Color = Color(0xFF003333)
public val beta_dark_error: Color = Color(0xFFFFB4AB)
public val beta_dark_onError: Color = Color(0xFF690005)
public val beta_dark_outline: Color = Color(0xFF8E9099)

// ====================
// PROD THEME COLORS (Royal Premium)
// Concept: Deep Purple + Luxury Gold
// ====================

// Primary accent color for Prod
public val ProdPrimaryColor: Color = Color(0xFFFFD700) // Luxury Gold / Amber

// Prod Light Theme
public val prod_light_background: Color = NeutralColors.Neutral50
public val prod_light_onBackground: Color = Color(0xFF1C1B1F)
public val prod_light_surface: Color = NeutralColors.Neutral100
public val prod_light_onSurface: Color = Color(0xFF1C1B1F)
public val prod_light_surfaceVariant: Color = NeutralColors.Neutral200
public val prod_light_onSurfaceVariant: Color = Color(0xFF49454E)
public val prod_light_primary: Color = Color(0xFF7B5800) // Darker gold for light theme
public val prod_light_onPrimary: Color = Color(0xFFFFFFFF)
public val prod_light_primaryContainer: Color = Color(0xFFFFDEA6)
public val prod_light_onPrimaryContainer: Color = Color(0xFF271900)
public val prod_light_secondary: Color = Color(0xFF6750A4) // Royal Purple
public val prod_light_onSecondary: Color = Color(0xFFFFFFFF)
public val prod_light_secondaryContainer: Color = Color(0xFFE9DDFF)
public val prod_light_onSecondaryContainer: Color = Color(0xFF22005D)
public val prod_light_tertiary: Color = Color(0xFF7B5800)
public val prod_light_onTertiary: Color = Color(0xFFFFFFFF)
public val prod_light_error: Color = Color(0xFFBA1A1A)
public val prod_light_onError: Color = Color(0xFFFFFFFF)
public val prod_light_outline: Color = Color(0xFF7A757F)

// Prod Dark Theme
public val prod_dark_background: Color = NeutralColors.Neutral900
public val prod_dark_onBackground: Color = Color(0xFFE6E1E6)
public val prod_dark_surface: Color = NeutralColors.Neutral900
public val prod_dark_onSurface: Color = Color(0xFFE6E1E6)
public val prod_dark_surfaceVariant: Color = NeutralColors.Neutral800
public val prod_dark_onSurfaceVariant: Color = Color(0xFFCAC4CF)
public val prod_dark_primary: Color = ProdPrimaryColor
public val prod_dark_onPrimary: Color = Color(0xFF402D00)
public val prod_dark_primaryContainer: Color = Color(0xFF5C4200)
public val prod_dark_onPrimaryContainer: Color = Color(0xFFFFDEA6)
public val prod_dark_secondary: Color = Color(0xFFCFBCFF) // Light Purple
public val prod_dark_onSecondary: Color = Color(0xFF381E72)
public val prod_dark_secondaryContainer: Color = Color(0xFF4F378A)
public val prod_dark_onSecondaryContainer: Color = Color(0xFFE9DDFF)
public val prod_dark_tertiary: Color = ProdPrimaryColor
public val prod_dark_onTertiary: Color = Color(0xFF402D00)
public val prod_dark_error: Color = Color(0xFFFFB4AB)
public val prod_dark_onError: Color = Color(0xFF690005)
public val prod_dark_outline: Color = Color(0xFF948F99)
