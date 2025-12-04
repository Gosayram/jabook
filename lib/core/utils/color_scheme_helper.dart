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

/// Helper class for working with color schemes (prod/beta).
///
/// This class provides methods to get primary and accent colors
/// based on whether the app is in beta mode or production mode.
class ColorSchemeHelper {
  /// Private constructor to prevent instantiation.
  ColorSchemeHelper._();

  /// Primary color for production mode (Violet).
  static const Color prodPrimary = Color(0xFF6B46C1);

  /// Accent color for production mode (Orange).
  static const Color prodAccent = Color(0xFFFF6B35);

  /// Primary color for beta mode (Graphite Blue).
  static const Color betaPrimary = Color(0xFF263B52);

  /// Accent color for beta mode (Bright Red).
  static const Color betaAccent = Color(0xFFD64545);

  /// Background color for production dark theme.
  static const Color prodBackgroundDark = Color(0xFF1E1B4B);

  /// Surface color for production dark theme.
  static const Color prodSurfaceDark = Color(0xFF2D2A4A);

  /// Background color for beta dark theme (Olive Green).
  static const Color betaBackgroundDark = Color(0xFFA9B65F);

  /// Surface color for beta dark theme (Cream).
  static const Color betaSurfaceDark = Color(0xFFF2E4C9);

  /// Gets the primary color based on beta mode.
  static Color getPrimaryColor(bool isBeta) =>
      isBeta ? betaPrimary : prodPrimary;

  /// Gets the accent color based on beta mode.
  static Color getAccentColor(bool isBeta) => isBeta ? betaAccent : prodAccent;

  /// Gets the background color for dark theme based on beta mode.
  static Color getBackgroundDarkColor(bool isBeta) =>
      isBeta ? betaBackgroundDark : prodBackgroundDark;

  /// Gets the surface color for dark theme based on beta mode.
  static Color getSurfaceDarkColor(bool isBeta) =>
      isBeta ? betaSurfaceDark : prodSurfaceDark;
}
