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
import 'package:jabook/core/auth/guest_mode_restrictions.dart';
import 'package:jabook/core/domain/auth/entities/credential_dialog_result.dart';
import 'package:jabook/core/domain/auth/entities/user_access_level.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/auth/presentation/widgets/credential_request_dialog.dart';
import 'package:riverpod/legacy.dart';

/// State for access control management.
class AccessState {
  /// Creates a new AccessState instance.
  const AccessState({
    this.accessLevel = UserAccessLevel.guest,
    this.isCredentialDialogShown = false,
  });

  /// Current access level of the user.
  final UserAccessLevel accessLevel;

  /// Whether credential dialog is currently shown.
  final bool isCredentialDialogShown;

  /// Creates a copy of this state with updated values.
  AccessState copyWith({
    UserAccessLevel? accessLevel,
    bool? isCredentialDialogShown,
  }) =>
      AccessState(
        accessLevel: accessLevel ?? this.accessLevel,
        isCredentialDialogShown:
            isCredentialDialogShown ?? this.isCredentialDialogShown,
      );

  /// Returns whether the user is in guest mode.
  bool get isGuest => accessLevel.isGuest;

  /// Returns whether the user has full access.
  bool get isFull => accessLevel.isFull;
}

/// Notifier for managing user access control.
///
/// This class manages the access level of the user (guest or full)
/// and provides methods to check feature access and upgrade access level.
class AccessNotifier extends StateNotifier<AccessState> {
  /// Creates a new AccessNotifier instance.
  AccessNotifier() : super(const AccessState());

  /// Checks if the user can access a specific feature.
  ///
  /// Returns `true` if the feature is accessible, `false` otherwise.
  bool canAccessFeature(String feature) {
    switch (state.accessLevel) {
      case UserAccessLevel.guest:
        final isRestricted = GuestModeRestrictions.isRestricted(feature);
        if (isRestricted) {
          StructuredLogger().log(
            level: 'info',
            subsystem: 'access',
            message: 'Feature access denied in guest mode',
            extra: {'feature': feature},
          );
        }
        return !isRestricted;
      case UserAccessLevel.full:
        return true;
    }
  }

  /// Sets the credential dialog shown state.
  void setCredentialDialogShown(bool shown) {
    state = state.copyWith(isCredentialDialogShown: shown);
  }

  /// Upgrades the user to full access.
  void upgradeToFullAccess() {
    if (state.accessLevel == UserAccessLevel.guest) {
      StructuredLogger().log(
        level: 'info',
        subsystem: 'access',
        message: 'Upgrading user to full access',
      );
      state = state.copyWith(accessLevel: UserAccessLevel.full);
    }
  }

  /// Sets the user to guest mode.
  void setGuestMode() {
    if (state.accessLevel == UserAccessLevel.full) {
      StructuredLogger().log(
        level: 'info',
        subsystem: 'access',
        message: 'Setting user to guest mode',
      );
      state = state.copyWith(accessLevel: UserAccessLevel.guest);
    }
  }

  /// Initializes access level based on authentication status.
  ///
  /// If the user is authenticated, sets full access.
  /// Otherwise, sets guest mode.
  void initializeAccessLevel(bool isAuthenticated) {
    final newAccessLevel =
        isAuthenticated ? UserAccessLevel.full : UserAccessLevel.guest;
    if (state.accessLevel != newAccessLevel) {
      StructuredLogger().log(
        level: 'info',
        subsystem: 'access',
        message: 'Initializing access level',
        extra: {
          'isAuthenticated': isAuthenticated,
          'accessLevel': newAccessLevel.name,
        },
      );
      state = state.copyWith(accessLevel: newAccessLevel);
    }
  }

  /// Requests access to a feature, showing credential dialog if needed.
  ///
  /// Returns `true` if access is granted, `false` otherwise.
  /// If the user is in guest mode and the feature is restricted,
  /// shows a dialog to request credentials.
  Future<bool> requestAccess(BuildContext context, String feature) async {
    // If access is already granted, return true
    if (canAccessFeature(feature)) {
      return true;
    }

    // If dialog is already shown, don't show another one
    if (state.isCredentialDialogShown) {
      return false;
    }

    // Mark dialog as shown
    setCredentialDialogShown(true);

    try {
      // Show credential request dialog
      final result = await CredentialRequestDialog.show(context, feature);

      // Reset dialog shown state
      setCredentialDialogShown(false);

      if (result == CredentialDialogResult.signIn) {
        // User chose to sign in - return true to allow navigation to auth screen
        safeUnawaited(
          StructuredLogger().log(
            level: 'info',
            subsystem: 'access',
            message: 'User chose to sign in',
            extra: {'feature': feature},
          ),
        );
        return true;
      } else if (result == CredentialDialogResult.upgrade) {
        // User chose to upgrade - upgrade to full access
        upgradeToFullAccess();
        safeUnawaited(
          StructuredLogger().log(
            level: 'info',
            subsystem: 'access',
            message: 'User upgraded to full access',
            extra: {'feature': feature},
          ),
        );
        return true;
      } else {
        // User cancelled
        safeUnawaited(
          StructuredLogger().log(
            level: 'info',
            subsystem: 'access',
            message: 'User cancelled credential request',
            extra: {'feature': feature},
          ),
        );
        return false;
      }
    } on Exception catch (e) {
      // Reset dialog shown state on error
      setCredentialDialogShown(false);
      safeUnawaited(
        StructuredLogger().log(
          level: 'error',
          subsystem: 'access',
          message: 'Error showing credential dialog',
          cause: e.toString(),
          extra: {'feature': feature},
        ),
      );
      return false;
    }
  }
}
