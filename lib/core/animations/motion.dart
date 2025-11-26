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

import 'package:animations/animations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/animations/animation_utils.dart';
import 'package:jabook/core/utils/responsive_utils.dart';

/// Configuration for Material Motion animations.
///
/// Provides pre-configured animation builders for different use cases
/// in the application, adapted for Android devices of various sizes.
class Motion {
  /// Private constructor to prevent instantiation.
  const Motion._();

  /// Creates a container transform transition for card to detail screen.
  ///
  /// Used for transitions like AudiobookCard → TopicScreen.
  /// Duration is adapted based on screen size and user preferences.
  static Future<ContainerTransitionType> getContainerTransitionType(
    BuildContext context,
  ) async {
    final shouldDisable = await AnimationUtils.shouldDisableAnimations(context);
    if (shouldDisable) {
      return ContainerTransitionType.fade;
    }
    return ContainerTransitionType.fadeThrough;
  }

  /// Gets border radius for cards based on screen size.
  ///
  /// Returns smaller radius on small screens, larger on big screens.
  static double getCardBorderRadius(BuildContext context) {
    if (ResponsiveUtils.isVerySmallScreen(context)) {
      return 8.0;
    } else if (ResponsiveUtils.isTablet(context) ||
        ResponsiveUtils.isDesktop(context)) {
      return 16.0;
    }
    return 12.0;
  }

  /// Creates a fade through transition for bottom navigation.
  ///
  /// Used for switching between main tabs (Library, Search, Downloads, Settings).
  static Widget fadeThroughTransition({
    required BuildContext context,
    required Animation<double> animation,
    required Animation<double> secondaryAnimation,
    required Widget child,
  }) =>
      FadeThroughTransition(
        animation: animation,
        secondaryAnimation: secondaryAnimation,
        child: child,
      );

  /// Creates a shared axis transition for settings navigation.
  ///
  /// Used for transitions like Settings → About.
  /// [axis] determines the direction of the transition.
  static Widget sharedAxisTransition({
    required BuildContext context,
    required Animation<double> animation,
    required Animation<double> secondaryAnimation,
    required Widget child,
    SharedAxisTransitionType type = SharedAxisTransitionType.horizontal,
  }) =>
      SharedAxisTransition(
        animation: animation,
        secondaryAnimation: secondaryAnimation,
        transitionType: type,
        child: child,
      );

  /// Creates a fade scale transition for dialogs and bottom sheets.
  ///
  /// Used for modal dialogs and bottom sheets.
  static Widget fadeScaleTransition({
    required BuildContext context,
    required Animation<double> animation,
    required Widget child,
  }) =>
      FadeScaleTransition(
        animation: animation,
        child: child,
      );

  /// Gets the scale value for fade scale transitions.
  ///
  /// Returns scale from 0.96 to 1.00 for smooth dialog appearance.
  static double getDialogScale(BuildContext context) {
    // Use smaller scale on very small screens to avoid overflow
    if (ResponsiveUtils.isVerySmallScreen(context)) {
      return 0.98;
    }
    return 0.96;
  }

  /// Creates page route transition for GoRouter.
  ///
  /// Returns a CustomTransitionPage with the specified transition.
  static Page<T> createPageRoute<T>({
    required Widget child,
    required String name,
    Object? arguments,
    String? restorationId,
    required Widget Function(
      BuildContext context,
      Animation<double> animation,
      Animation<double> secondaryAnimation,
      Widget child,
    ) transitionsBuilder,
  }) =>
      CustomTransitionPage<T>(
        child: child,
        name: name,
        arguments: arguments,
        restorationId: restorationId,
        transitionsBuilder: transitionsBuilder,
      );
}
