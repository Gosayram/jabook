import 'package:flutter/material.dart';

class AppTheme {
  static const Color _primaryColor = Color(0xFF6B46C1); // Violet
  static const Color _backgroundColor = Color(0xFF1E1B4B); // Dark violet
  static const Color _surfaceColor = Color(0xFF2D2A4A); // Darker surface
  static const Color _onSurfaceColor = Color(0xFFF5F5DC); // Beige
  static const Color _accentColor = Color(0xFFFF6B35); // Orange

  static ThemeData lightTheme = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.light(
      primary: _primaryColor,
      secondary: _accentColor,
      surface: _surfaceColor,
      onSurface: _onSurfaceColor,
      error: Colors.red,
      onError: Colors.white,
      brightness: Brightness.light,
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

  static ThemeData darkTheme = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.dark(
      primary: _primaryColor,
      secondary: _accentColor,
      surface: _surfaceColor,
      onSurface: _onSurfaceColor,
      error: Colors.red,
      onError: Colors.white,
      brightness: Brightness.dark,
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