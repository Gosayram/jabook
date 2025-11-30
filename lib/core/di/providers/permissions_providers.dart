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
import 'package:jabook/core/di/providers/utils_providers.dart';
import 'package:jabook/core/infrastructure/permissions/manufacturer_permissions_service.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service_v2.dart';

/// Provider for PermissionService instance.
///
/// This provider creates a PermissionService instance that can be used
/// throughout the application for permission management.
final permissionServiceProvider = Provider<PermissionService>((ref) {
  final deviceInfo = ref.read(deviceInfoUtilsProvider);
  final manufacturerService = ref.read(manufacturerPermissionsServiceProvider);
  return PermissionService(
    deviceInfo: deviceInfo,
    manufacturerPermissionsService: manufacturerService,
  );
});

/// Provider for PermissionServiceV2 instance.
///
/// This provider creates a PermissionServiceV2 instance that can be used
/// throughout the application for permission management using system APIs.
final permissionServiceV2Provider =
    Provider<PermissionServiceV2>((ref) => PermissionServiceV2());

/// Provider for ManufacturerPermissionsService instance.
///
/// This provider creates a ManufacturerPermissionsService instance that can be used
/// throughout the application for manufacturer-specific settings management.
final manufacturerPermissionsServiceProvider =
    Provider<ManufacturerPermissionsService>((ref) {
  final deviceInfo = ref.read(deviceInfoUtilsProvider);
  return ManufacturerPermissionsService(deviceInfo: deviceInfo);
});
