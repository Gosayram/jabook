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
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/features/auth/presentation/screens/restricted_feature_screen.dart';

/// Wrapper widget that checks feature access before displaying content.
///
/// This widget wraps a child widget and checks if the user has access
/// to the specified feature. In guest mode, restricted features are still
/// accessible but with limited functionality (read-only or with warnings).
/// If access is completely denied, shows a restricted feature screen.
class FeatureAccessWrapper extends ConsumerWidget {
  /// Creates a new FeatureAccessWrapper instance.
  const FeatureAccessWrapper({
    required this.feature,
    required this.child,
    this.restrictedChild,
    this.allowGuestAccess = true,
    super.key,
  });

  /// Name of the feature to check access for.
  final String feature;

  /// Widget to display if access is granted.
  final Widget child;

  /// Optional widget to display if access is denied.
  /// If not provided, uses [RestrictedFeatureScreen].
  final Widget? restrictedChild;

  /// Whether to allow guest access with limited functionality.
  /// If true, guest users can access the feature but with restrictions.
  /// If false, guest users are shown the restricted screen.
  final bool allowGuestAccess;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final accessState = ref.watch(accessProvider);
    final accessNotifier = ref.read(accessProvider.notifier);
    final canAccess = accessNotifier.canAccessFeature(feature);

    // If full access, show child normally
    if (canAccess) {
      return child;
    }

    // If guest mode and allowGuestAccess is true, show child with limited functionality
    // The child widget should check accessState.isGuest to show appropriate restrictions
    if (accessState.isGuest && allowGuestAccess) {
      return child;
    }

    // Otherwise, show restricted screen
    // The restricted screen will handle navigation to auth if user chooses to sign in
    return restrictedChild ?? RestrictedFeatureScreen(feature: feature);
  }
}
