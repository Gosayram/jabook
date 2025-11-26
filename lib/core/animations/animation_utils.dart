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
import 'package:shared_preferences/shared_preferences.dart';

/// Key for storing animation settings in SharedPreferences.
const String _reduceAnimationsKey = 'reduce_animations';

/// Utility class for managing animation settings and preferences.
class AnimationUtils {
  /// Private constructor to prevent instantiation.
  const AnimationUtils._();

  /// Checks if animations should be reduced based on user preference.
  ///
  /// Returns `true` if user has enabled "Reduce animations" setting.
  static Future<bool> shouldReduceAnimations() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getBool(_reduceAnimationsKey) ?? false;
    } on Exception {
      return false;
    }
  }

  /// Sets the "Reduce animations" preference.
  ///
  /// [value] is `true` to reduce animations, `false` to enable full animations.
  static Future<void> setReduceAnimations(bool value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_reduceAnimationsKey, value);
    } on Exception {
      // Ignore save errors
    }
  }

  /// Checks if system animations are disabled.
  ///
  /// Uses MediaQuery to check if system has disabled animations.
  static bool isSystemAnimationDisabled(BuildContext context) =>
      MediaQuery.of(context).disableAnimations;

  /// Checks if animations should be disabled completely.
  ///
  /// Returns `true` if either user preference or system setting disables animations.
  /// Note: This method should be called before using context in async operations.
  static Future<bool> shouldDisableAnimations(BuildContext context) async {
    // Check system setting first (synchronous)
    final systemDisabled = isSystemAnimationDisabled(context);
    if (systemDisabled) {
      return true;
    }
    // Check user preference (asynchronous)
    final userReduced = await shouldReduceAnimations();
    return userReduced;
  }

  /// Gets animation duration based on device capabilities and settings.
  ///
  /// [baseDuration] is the base duration for the animation.
  /// Returns adjusted duration based on screen size and settings.
  static Future<Duration> getAnimationDuration(
    BuildContext context,
    Duration baseDuration,
  ) async {
    // Save screen width before async operation to avoid BuildContext issues
    final screenWidth = MediaQuery.of(context).size.width;
    final shouldDisable = await shouldDisableAnimations(context);
    if (shouldDisable) {
      return const Duration(milliseconds: 100);
    }

    // Check if device is a large screen (tablet/desktop)
    if (screenWidth > 800) {
      // Increase duration by 10-15% for large screens
      return Duration(
        milliseconds: (baseDuration.inMilliseconds * 1.15).round(),
      );
    }

    return baseDuration;
  }

  /// Gets container transform duration.
  ///
  /// Returns duration optimized for container transform animations.
  static Future<Duration> getContainerTransformDuration(
    BuildContext context,
  ) async {
    const baseDuration = Duration(milliseconds: 300);
    return getAnimationDuration(context, baseDuration);
  }

  /// Gets fade transition duration.
  ///
  /// Returns duration optimized for fade transitions.
  static Future<Duration> getFadeTransitionDuration(
    BuildContext context,
  ) async {
    const baseDuration = Duration(milliseconds: 300);
    return getAnimationDuration(context, baseDuration);
  }

  /// Gets scale transition duration.
  ///
  /// Returns duration optimized for scale transitions.
  static Future<Duration> getScaleTransitionDuration(
    BuildContext context,
  ) async {
    const baseDuration = Duration(milliseconds: 200);
    return getAnimationDuration(context, baseDuration);
  }
}
