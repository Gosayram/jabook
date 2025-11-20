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
  static double getBorderRadius(BuildContext context, {double baseRadius = 12}) {
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
}

