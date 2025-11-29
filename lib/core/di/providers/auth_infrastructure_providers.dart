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

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/session/session_manager.dart';

/// Provider for CredentialManager instance.
///
/// This provider creates a single instance of CredentialManager that is shared
/// throughout the application lifecycle. The instance is created lazily when
/// first accessed.
final credentialManagerProvider =
    Provider<CredentialManager>((ref) => CredentialManager());

/// Provider for SessionManager instance.
///
/// This provider creates a SessionManager instance that depends on RuTrackerAuth.
/// The SessionManager is created lazily when first accessed.
///
/// Note: This provider requires rutrackerAuthProvider to be overridden with
/// a proper context in the widget tree.
final sessionManagerProvider = Provider<SessionManager>((ref) {
  final rutrackerAuth = ref.watch(rutrackerAuthProvider);
  return SessionManager(rutrackerAuth: rutrackerAuth);
});
