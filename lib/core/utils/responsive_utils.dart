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
import 'package:responsive_framework/responsive_framework.dart';

/// Utility class for responsive design helpers.
///
/// Provides convenient methods for adapting UI elements
/// to different screen sizes (mobile, tablet, desktop).
class ResponsiveUtils {
  /// Private constructor to prevent instantiation.
  const ResponsiveUtils._();

  /// Gets the current breakpoint name.
  ///
  /// Returns 'MOBILE', 'TABLET', 'DESKTOP', or '4K' based on screen width.
  static String getBreakpoint(BuildContext context) =>
      ResponsiveBreakpoints.of(context).breakpoint.name ?? 'MOBILE';

  /// Checks if the current screen is mobile.
  static bool isMobile(BuildContext context) =>
      ResponsiveBreakpoints.of(context).isMobile;

  /// Checks if the current screen is tablet.
  static bool isTablet(BuildContext context) =>
      ResponsiveBreakpoints.of(context).isTablet;

  /// Checks if the current screen is desktop.
  static bool isDesktop(BuildContext context) =>
      ResponsiveBreakpoints.of(context).isDesktop;

  /// Gets responsive padding based on screen size.
  ///
  /// Returns larger padding for larger screens.
  static EdgeInsets getResponsivePadding(BuildContext context) {
    if (isDesktop(context)) {
      return const EdgeInsets.symmetric(horizontal: 32, vertical: 16);
    } else if (isTablet(context)) {
      return const EdgeInsets.symmetric(horizontal: 24, vertical: 16);
    } else {
      return const EdgeInsets.all(16);
    }
  }

  /// Gets responsive horizontal padding.
  static double getHorizontalPadding(BuildContext context) {
    if (isDesktop(context)) {
      return 32;
    } else if (isTablet(context)) {
      return 24;
    } else {
      return 16;
    }
  }

  /// Gets responsive column count for grid layouts.
  ///
  /// Returns appropriate number of columns based on screen size.
  static int getColumnCount(BuildContext context) {
    if (isDesktop(context)) {
      return 4;
    } else if (isTablet(context)) {
      return 3;
    } else {
      return 2;
    }
  }

  /// Gets responsive card width for list items.
  ///
  /// Returns null for mobile (full width), or specific width for larger screens.
  static double? getCardWidth(BuildContext context) {
    if (isDesktop(context)) {
      return 300;
    } else if (isTablet(context)) {
      return 250;
    } else {
      return null; // Full width on mobile
    }
  }

  /// Gets responsive font size multiplier.
  ///
  /// Returns larger multiplier for larger screens.
  static double getFontSizeMultiplier(BuildContext context) {
    if (isDesktop(context)) {
      return 1.2;
    } else if (isTablet(context)) {
      return 1.1;
    } else {
      return 1.0;
    }
  }

  /// Gets responsive icon size.
  static double getIconSize(BuildContext context, {double baseSize = 24}) {
    if (isDesktop(context)) {
      return baseSize * 1.3;
    } else if (isTablet(context)) {
      return baseSize * 1.15;
    } else {
      return baseSize;
    }
  }

  /// Gets responsive max width for content containers.
  ///
  /// Prevents content from becoming too wide on large screens.
  static double? getMaxContentWidth(BuildContext context) {
    if (isDesktop(context)) {
      return 1200;
    } else if (isTablet(context)) {
      return 800;
    } else {
      return null; // No max width on mobile
    }
  }

  /// Gets responsive spacing between elements.
  static double getSpacing(BuildContext context, {double baseSpacing = 8}) {
    if (isDesktop(context)) {
      return baseSpacing * 1.5;
    } else if (isTablet(context)) {
      return baseSpacing * 1.25;
    } else {
      return baseSpacing;
    }
  }

  /// Gets responsive border radius.
  static double getBorderRadius(BuildContext context,
      {double baseRadius = 12}) {
    if (isDesktop(context)) {
      return baseRadius * 1.2;
    } else if (isTablet(context)) {
      return baseRadius * 1.1;
    } else {
      return baseRadius;
    }
  }

  /// Wraps a widget with responsive constraints.
  ///
  /// Applies max width and padding based on screen size.
  static Widget responsiveContainer(
    BuildContext context,
    Widget child, {
    EdgeInsets? padding,
    double? maxWidth,
  }) =>
      Container(
        constraints: BoxConstraints(
          maxWidth: maxWidth ?? getMaxContentWidth(context) ?? double.infinity,
        ),
        padding: padding ?? getResponsivePadding(context),
        child: child,
      );

  /// Gets responsive number of items per row in grid.
  static int getItemsPerRow(BuildContext context) {
    if (isDesktop(context)) {
      return 4;
    } else if (isTablet(context)) {
      return 3;
    } else {
      return 2;
    }
  }

  /// Gets responsive dialog width.
  static double? getDialogWidth(BuildContext context) {
    if (isDesktop(context)) {
      return 600;
    } else if (isTablet(context)) {
      return 500;
    } else {
      return null; // Full width on mobile
    }
  }

  /// Gets responsive bottom navigation bar height.
  static double getBottomNavHeight(BuildContext context) {
    if (isDesktop(context)) {
      return 72;
    } else if (isTablet(context)) {
      return 64;
    } else {
      return 56;
    }
  }

  /// Checks if the current screen is very small (<360px width).
  ///
  /// Very small screens require special handling for compact layouts.
  static bool isVerySmallScreen(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    return screenWidth < 360;
  }

  /// Checks if the current screen is a large tablet (>800px width).
  ///
  /// Large tablets can use more desktop-like layouts.
  static bool isLargeTablet(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    return screenWidth > 800 && isTablet(context);
  }

  /// Gets responsive width for recommended audiobook cards.
  ///
  /// Returns wider cards on small screens to ensure both title and author are visible.
  static double getRecommendedCardWidth(BuildContext context) {
    if (isVerySmallScreen(context)) {
      return 170.0; // Wider for very small screens
    } else if (isMobile(context)) {
      final screenWidth = MediaQuery.of(context).size.width;
      // Use wider cards on small screens (<400px) to show both title and author
      return screenWidth < 400 ? 160.0 : 140.0;
    } else if (isTablet(context)) {
      return 160.0; // Slightly wider on tablets
    } else {
      return 180.0; // Wider on desktop
    }
  }

  /// Gets responsive dialog height.
  ///
  /// Returns appropriate height based on screen size and content type.
  /// The [contentType] parameter can be 'compact', 'normal', or 'tall'.
  static double? getDialogHeight(
    BuildContext context, {
    String contentType = 'normal',
  }) {
    final screenHeight = MediaQuery.of(context).size.height;

    if (isVerySmallScreen(context)) {
      // Very small screens: use percentage of screen height
      switch (contentType) {
        case 'compact':
          return screenHeight * 0.4;
        case 'tall':
          return screenHeight * 0.85;
        default:
          return screenHeight * 0.6;
      }
    } else if (isMobile(context)) {
      // Mobile screens
      switch (contentType) {
        case 'compact':
          return 300;
        case 'tall':
          return screenHeight * 0.8;
        default:
          return 400;
      }
    } else if (isTablet(context)) {
      // Tablet screens
      switch (contentType) {
        case 'compact':
          return 400;
        case 'tall':
          return screenHeight * 0.75;
        default:
          return 500;
      }
    } else {
      // Desktop screens
      switch (contentType) {
        case 'compact':
          return 450;
        case 'tall':
          return screenHeight * 0.7;
        default:
          return 600;
      }
    }
  }

  /// Gets minimum touch target size for mobile devices.
  ///
  /// Returns 44.0 pixels, which is the recommended minimum touch target size
  /// for mobile interfaces according to Material Design and iOS guidelines.
  static double getMinTouchTarget(BuildContext context) => 44.0;

  /// Gets compact padding for very small screens.
  ///
  /// Returns reduced padding on very small screens to save space.
  static EdgeInsets getCompactPadding(BuildContext context) {
    if (isVerySmallScreen(context)) {
      return const EdgeInsets.all(8); // Reduced padding for very small screens
    }
    return getResponsivePadding(context);
  }

  /// Gets responsive title font size.
  ///
  /// Returns appropriate font size for titles based on screen size.
  /// The [baseSize] parameter is the base font size (default: 20).
  static double getTitleFontSize(BuildContext context, {double baseSize = 20}) {
    if (isVerySmallScreen(context)) {
      return baseSize * 0.85; // Smaller on very small screens
    }
    return baseSize * getFontSizeMultiplier(context);
  }

  /// Gets responsive body font size.
  ///
  /// Returns appropriate font size for body text based on screen size.
  /// The [baseSize] parameter is the base font size (default: 14).
  static double getBodyFontSize(BuildContext context, {double baseSize = 14}) {
    if (isVerySmallScreen(context)) {
      return baseSize * 0.9; // Slightly smaller on very small screens
    }
    return baseSize * getFontSizeMultiplier(context);
  }
}
