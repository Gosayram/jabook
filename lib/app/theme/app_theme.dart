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
  static ThemeData get lightTheme => _buildLightTheme();

  /// Gets the dark theme configuration.
  ///
  /// Returns a ThemeData object configured for dark mode with
  /// custom colors and styling.
  static ThemeData get darkTheme => _buildDarkTheme();

  static ThemeData _buildLightTheme() => ThemeData(
        useMaterial3: true,
        colorScheme: const ColorScheme.light(
          primary: _primaryColor,
          secondary: _accentColor,
          surface: _surfaceColor,
          onSurface: _onSurfaceColor,
          error: Colors.red,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: _primaryColor,
          foregroundColor: Colors.white,
          elevation: 0,
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: _accentColor,
            foregroundColor: Colors.white,
            elevation: 2,
          ),
        ),
        textTheme: const TextTheme(
          bodyLarge: TextStyle(color: _onSurfaceColor),
          bodyMedium: TextStyle(color: _onSurfaceColor),
          bodySmall: TextStyle(color: _onSurfaceColor),
        ),
        cardTheme: CardThemeData(
          color: _surfaceColor,
          elevation: 2,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      );

  static ThemeData _buildDarkTheme() => ThemeData(
        useMaterial3: true,
        colorScheme: const ColorScheme.dark(
          primary: _primaryColor,
          secondary: _accentColor,
          surface: _surfaceColor,
          onSurface: _onSurfaceColor,
          error: Colors.red,
          onError: Colors.white,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: _backgroundColor,
          foregroundColor: _onSurfaceColor,
          elevation: 0,
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: _accentColor,
            foregroundColor: Colors.white,
            elevation: 2,
          ),
        ),
        textTheme: const TextTheme(
          bodyLarge: TextStyle(color: _onSurfaceColor),
          bodyMedium: TextStyle(color: _onSurfaceColor),
          bodySmall: TextStyle(color: _onSurfaceColor),
        ),
        cardTheme: CardThemeData(
          color: _surfaceColor,
          elevation: 2,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      );
}
