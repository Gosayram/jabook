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

import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Utility class for accessibility-related constants and helpers.
///
/// This class provides constants and methods to ensure the app
/// meets accessibility guidelines, including minimum touch target sizes
/// and contrast requirements.
class AccessibilityUtils {
  /// Private constructor to prevent instantiation.
  AccessibilityUtils._();

  /// Minimum touch target size recommended by Material Design (48x48 dp).
  ///
  /// This ensures buttons and interactive elements are large enough
  /// for comfortable tapping.
  static const double minTouchTargetSize = 48.0;

  /// Minimum touch target size for small screens (44x44 dp).
  ///
  /// Used on very small screens where 48dp might be too large.
  static const double minTouchTargetSizeSmall = 44.0;

  /// Gets the minimum touch target size based on screen size.
  ///
  /// Returns smaller size on very small screens, standard size otherwise.
  static double getMinTouchTargetSize(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    if (screenWidth < 360) {
      return minTouchTargetSizeSmall;
    }
    return minTouchTargetSize;
  }

  /// Creates constraints for minimum touch target size.
  ///
  /// Returns BoxConstraints that ensure the widget meets
  /// minimum touch target size requirements.
  static BoxConstraints getMinTouchTargetConstraints(BuildContext context) {
    final minSize = getMinTouchTargetSize(context);
    return BoxConstraints(
      minWidth: minSize,
      minHeight: minSize,
    );
  }

  /// Checks if a color meets WCAG contrast requirements.
  ///
  /// [foreground] is the text/icon color.
  /// [background] is the background color.
  /// [level] is the WCAG level ('AA' or 'AAA').
  ///
  /// Returns `true` if contrast ratio meets the specified level.
  static bool meetsContrastRatio(
    Color foreground,
    Color background,
    String level,
  ) {
    final ratio = _calculateContrastRatio(foreground, background);
    if (level == 'AAA') {
      return ratio >= 7.0; // AAA requires 7:1 for normal text
    } else {
      return ratio >= 4.5; // AA requires 4.5:1 for normal text
    }
  }

  /// Calculates the contrast ratio between two colors.
  ///
  /// Returns a value between 1 and 21, where 21 is maximum contrast.
  static double _calculateContrastRatio(Color color1, Color color2) {
    final luminance1 = _getRelativeLuminance(color1);
    final luminance2 = _getRelativeLuminance(color2);

    final lighter = luminance1 > luminance2 ? luminance1 : luminance2;
    final darker = luminance1 < luminance2 ? luminance1 : luminance2;

    return (lighter + 0.05) / (darker + 0.05);
  }

  /// Calculates the relative luminance of a color.
  ///
  /// Returns a value between 0 and 1.
  static double _getRelativeLuminance(Color color) {
    final r =
        _linearizeComponent((color.r * 255.0).round().clamp(0, 255) / 255.0);
    final g =
        _linearizeComponent((color.g * 255.0).round().clamp(0, 255) / 255.0);
    final b =
        _linearizeComponent((color.b * 255.0).round().clamp(0, 255) / 255.0);

    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  /// Linearizes a color component for luminance calculation.
  static double _linearizeComponent(double component) {
    if (component <= 0.03928) {
      return component / 12.92;
    } else {
      return math.pow((component + 0.055) / 1.055, 2.4).toDouble();
    }
  }
}
