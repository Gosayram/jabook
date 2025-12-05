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
import 'package:jabook/core/data/remote/network/user_agent_manager.dart';
import 'package:jabook/core/di/providers/config_providers.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:jabook/core/utils/network_utils.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';

/// Provider for StoragePathUtils instance.
///
/// This provider creates a StoragePathUtils instance that can be used
/// throughout the application for managing storage paths.
final storagePathUtilsProvider =
    Provider<StoragePathUtils>((ref) => StoragePathUtils());

/// Provider for UserAgentManager instance.
///
/// This provider creates a UserAgentManager instance that can be used
/// throughout the application for managing User-Agent strings.
final userAgentManagerProvider =
    Provider<UserAgentManager>((ref) => UserAgentManager());

/// Provider for NetworkUtils instance.
///
/// This provider creates a NetworkUtils instance that can be used
/// throughout the application for network connectivity checks.
final networkUtilsProvider = Provider<NetworkUtils>((ref) => NetworkUtils());

/// Provider for DeviceInfoUtils instance.
///
/// This provider creates a DeviceInfoUtils instance that can be used
/// throughout the application for device information.
final deviceInfoUtilsProvider =
    Provider<DeviceInfoUtils>((ref) => DeviceInfoUtils());

/// Provider for EnvironmentLogger instance.
///
/// This provider creates an EnvironmentLogger instance that can be used
/// throughout the application for environment-aware logging.
final environmentLoggerProvider = Provider<EnvironmentLogger>((ref) {
  final appConfig = ref.read(appConfigProvider);
  return EnvironmentLogger(appConfig: appConfig);
});
