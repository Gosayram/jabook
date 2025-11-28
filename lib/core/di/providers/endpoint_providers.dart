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
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';

/// Provider for EndpointManager instance.
///
/// This provider creates an EndpointManager instance using the app database.
final endpointManagerProvider = Provider<EndpointManager>((ref) {
  final appDatabase = ref.watch(appDatabaseProvider);
  return EndpointManager(appDatabase.database);
});

