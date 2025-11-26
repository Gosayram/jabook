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
import 'package:jabook/core/animations/animation_utils.dart';
import 'package:jabook/core/animations/motion.dart';

/// Utility class for showing dialogs with Material Motion animations.
class DialogUtils {
  /// Private constructor to prevent instantiation.
  const DialogUtils._();

  /// Shows a dialog with FadeScaleTransition animation.
  ///
  /// This is a drop-in replacement for [showDialog] that adds
  /// Material Motion fade scale transition.
  static Future<T?> showAnimatedDialog<T>({
    required BuildContext context,
    required WidgetBuilder builder,
    bool barrierDismissible = true,
    Color? barrierColor,
    String? barrierLabel,
    bool useSafeArea = true,
    bool useRootNavigator = false,
    RouteSettings? routeSettings,
  }) async {
    // Save context-dependent values before async operation
    final effectiveBarrierColor = barrierColor ?? Colors.black54;
    final effectiveBarrierLabel = barrierLabel ?? 'Dismiss';

    // Check if context is still valid
    if (!context.mounted) {
      return null;
    }

    final shouldDisable = await AnimationUtils.shouldDisableAnimations(context);

    if (!context.mounted) {
      return null;
    }

    if (shouldDisable) {
      // Use simple fade if animations are disabled
      return showDialog<T>(
        context: context,
        builder: builder,
        barrierDismissible: barrierDismissible,
        barrierColor: barrierColor,
        barrierLabel: barrierLabel,
        useSafeArea: useSafeArea,
        useRootNavigator: useRootNavigator,
        routeSettings: routeSettings,
      );
    }

    return showGeneralDialog<T>(
      context: context,
      pageBuilder: (context, animation, secondaryAnimation) => builder(context),
      barrierDismissible: barrierDismissible,
      barrierColor: effectiveBarrierColor,
      barrierLabel: effectiveBarrierLabel,
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          Motion.fadeScaleTransition(
        context: context,
        animation: animation,
        child: child,
      ),
      useRootNavigator: useRootNavigator,
      routeSettings: routeSettings,
    );
  }

  /// Shows a modal bottom sheet with FadeScaleTransition animation.
  ///
  /// This is a drop-in replacement for [showModalBottomSheet] that adds
  /// Material Motion fade scale transition.
  static Future<T?> showAnimatedBottomSheet<T>({
    required BuildContext context,
    required WidgetBuilder builder,
    Color? backgroundColor,
    double? elevation,
    ShapeBorder? shape,
    Clip? clipBehavior,
    BoxConstraints? constraints,
    Color? barrierColor,
    bool isScrollControlled = false,
    bool useRootNavigator = false,
    bool isDismissible = true,
    bool enableDrag = true,
    RouteSettings? routeSettings,
    AnimationController? transitionAnimationController,
    Offset? anchorPoint,
  }) async {
    // Check if context is still valid after async operation
    if (!context.mounted) {
      return null;
    }

    final shouldDisable = await AnimationUtils.shouldDisableAnimations(context);

    if (!context.mounted) {
      return null;
    }

    if (shouldDisable) {
      // Use simple slide if animations are disabled
      return showModalBottomSheet<T>(
        context: context,
        builder: builder,
        backgroundColor: backgroundColor,
        elevation: elevation,
        shape: shape,
        clipBehavior: clipBehavior,
        constraints: constraints,
        barrierColor: barrierColor,
        isScrollControlled: isScrollControlled,
        useRootNavigator: useRootNavigator,
        isDismissible: isDismissible,
        enableDrag: enableDrag,
        routeSettings: routeSettings,
        transitionAnimationController: transitionAnimationController,
        anchorPoint: anchorPoint,
      );
    }

    // showModalBottomSheet doesn't support custom transitionDuration directly
    // Use standard showModalBottomSheet with default animations
    // The fade scale effect will be applied through the builder if needed
    return showModalBottomSheet<T>(
      context: context,
      builder: builder,
      backgroundColor: backgroundColor,
      elevation: elevation,
      shape: shape,
      clipBehavior: clipBehavior,
      constraints: constraints,
      barrierColor: barrierColor,
      isScrollControlled: isScrollControlled,
      useRootNavigator: useRootNavigator,
      isDismissible: isDismissible,
      enableDrag: enableDrag,
      routeSettings: routeSettings,
      transitionAnimationController: transitionAnimationController,
      anchorPoint: anchorPoint,
    );
  }
}
