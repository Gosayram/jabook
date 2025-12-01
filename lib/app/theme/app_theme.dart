// Copyright 2025 Jabook Contributors
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

import 'package:flutter/material.dart';

/// Provides theme configuration for the JaBook application.
///
/// This mixin contains all color definitions and theme settings for both
/// light and dark modes, ensuring consistent styling throughout the app.
mixin AppTheme {
  /// Primary color (Violet) used for main UI elements
  static const Color _primaryColor = Color(0xFF6B46C1); // Violet

  /// Background color for dark mode (Dark violet)
  static const Color _backgroundColor = Color(0xFF1E1B4B); // Dark violet

  /// Surface color for cards and elevated components (Darker surface)
  static const Color _surfaceColor = Color(0xFF2D2A4A); // Darker surface

  /// Text and icon color for surfaces (Light gray with better contrast)
  static const Color _onSurfaceColor = Color(0xFFE8E8E8); // Light gray

  /// Accent color for highlights and secondary elements (Orange)
  static const Color _accentColor = Color(0xFFFF6B35); // Orange

  /// Gets the light theme configuration.
  ///
  /// Returns a ThemeData object configured for light mode with
  /// custom colors and styling.
  ///
  /// [highContrast] enables high contrast mode for better accessibility.
  /// [isBeta] enables beta color scheme when true.
  static ThemeData lightTheme(
          {bool highContrast = false, bool isBeta = false}) =>
      _buildLightTheme(highContrast: highContrast, isBeta: isBeta);

  /// Gets the dark theme configuration.
  ///
  /// Returns a ThemeData object configured for dark mode with
  /// custom colors and styling.
  ///
  /// [highContrast] enables high contrast mode for better accessibility.
  /// [isBeta] enables beta color scheme when true.
  static ThemeData darkTheme(
          {bool highContrast = false, bool isBeta = false}) =>
      _buildDarkTheme(highContrast: highContrast, isBeta: isBeta);

  // Beta color scheme constants
  static const Color _betaPrimaryColor = Color(0xFF263B52); // Graphite blue
  static const Color _betaBackgroundColor = Color(0xFFA9B65F); // Olive green
  static const Color _betaAccentColor = Color(0xFFD64545); // Bright red
  static const Color _betaSurfaceColor = Color(0xFFF2E4C9); // Cream

  static ThemeData _buildLightTheme(
      {bool highContrast = false, bool isBeta = false}) {
    // Beta color scheme takes precedence
    final primaryColor = isBeta
        ? _betaPrimaryColor
        : (highContrast
            ? const Color(0xFF000000) // Black for high contrast
            : _primaryColor);
    final surfaceColor = isBeta
        ? _betaSurfaceColor
        : (highContrast
            ? const Color(0xFFF5F5F5) // Light gray for high contrast
            : _surfaceColor);
    final onSurfaceColor = isBeta
        ? _betaPrimaryColor // Use primary color for text on beta surface
        : (highContrast
            ? const Color(0xFF000000) // Black text for high contrast
            : _onSurfaceColor);
    final accentColor = isBeta
        ? _betaAccentColor
        : (highContrast
            ? const Color(0xFF0000FF) // Blue for high contrast
            : _accentColor);

    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.light(
        primary: primaryColor,
        secondary: accentColor,
        surface: surfaceColor,
        onSurface: onSurfaceColor,
        error: highContrast ? const Color(0xFFCC0000) : Colors.red,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: primaryColor,
        foregroundColor: highContrast ? Colors.white : Colors.white,
        elevation: highContrast ? 4 : 0,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: accentColor,
          foregroundColor: highContrast ? Colors.white : Colors.white,
          elevation: highContrast ? 4 : 2,
          side: highContrast ? const BorderSide(width: 2) : null,
        ),
      ),
      textTheme: TextTheme(
        bodyLarge: TextStyle(
          color: onSurfaceColor,
          fontWeight: highContrast ? FontWeight.bold : FontWeight.normal,
        ),
        bodyMedium: TextStyle(
          color: onSurfaceColor,
          fontWeight: highContrast ? FontWeight.bold : FontWeight.normal,
        ),
        bodySmall: TextStyle(
          color: onSurfaceColor,
          fontWeight: highContrast ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      cardTheme: CardThemeData(
        color: surfaceColor,
        elevation: highContrast ? 4 : 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: highContrast ? const BorderSide(width: 2) : BorderSide.none,
        ),
      ),
    );
  }

  static ThemeData _buildDarkTheme(
      {bool highContrast = false, bool isBeta = false}) {
    // Beta color scheme takes precedence
    final primaryColor = isBeta
        ? _betaPrimaryColor
        : (highContrast
            ? const Color(0xFFFFFFFF) // White for high contrast
            : _primaryColor);
    final backgroundColor = isBeta
        ? _betaBackgroundColor
        : (highContrast
            ? const Color(0xFF000000) // Black for high contrast
            : _backgroundColor);
    final surfaceColor = isBeta
        ? _betaSurfaceColor
        : (highContrast
            ? const Color(0xFF1A1A1A) // Dark gray for high contrast
            : _surfaceColor);
    final onSurfaceColor = isBeta
        ? _betaPrimaryColor // Use primary color for text on beta surface
        : (highContrast
            ? const Color(0xFFFFFFFF) // White text for high contrast
            : _onSurfaceColor);
    final accentColor = isBeta
        ? _betaAccentColor
        : (highContrast
            ? const Color(0xFFFFFF00) // Yellow for high contrast
            : _accentColor);

    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.dark(
        primary: primaryColor,
        secondary: accentColor,
        surface: surfaceColor,
        onSurface: onSurfaceColor,
        error: highContrast ? const Color(0xFFFF4444) : Colors.red,
        onError: Colors.white,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: backgroundColor,
        foregroundColor: onSurfaceColor,
        elevation: highContrast ? 4 : 0,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: accentColor,
          foregroundColor: highContrast ? Colors.black : Colors.white,
          elevation: highContrast ? 4 : 2,
          side: highContrast
              ? const BorderSide(color: Colors.white, width: 2)
              : null,
        ),
      ),
      textTheme: TextTheme(
        bodyLarge: TextStyle(
          color: onSurfaceColor,
          fontWeight: highContrast ? FontWeight.bold : FontWeight.normal,
        ),
        bodyMedium: TextStyle(
          color: onSurfaceColor,
          fontWeight: highContrast ? FontWeight.bold : FontWeight.normal,
        ),
        bodySmall: TextStyle(
          color: onSurfaceColor,
          fontWeight: highContrast ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      cardTheme: CardThemeData(
        color: surfaceColor,
        elevation: highContrast ? 4 : 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: highContrast
              ? const BorderSide(color: Colors.white, width: 2)
              : BorderSide.none,
        ),
      ),
    );
  }
}
