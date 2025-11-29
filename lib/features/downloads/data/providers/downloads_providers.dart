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
import 'package:jabook/core/di/providers/torrent_providers.dart'
    as core_torrent;

/// Provider for GetActiveTasksUseCase instance.
///
/// This provider re-exports the use case from core torrent providers
/// for use in the downloads feature.
final getActiveTasksUseCaseProvider =
    Provider((ref) => ref.watch(core_torrent.getActiveTasksUseCaseProvider));

/// Provider for PauseTorrentUseCase instance.
final pauseTorrentUseCaseProvider =
    Provider((ref) => ref.watch(core_torrent.pauseTorrentUseCaseProvider));

/// Provider for ResumeTorrentUseCase instance.
final resumeTorrentUseCaseProvider =
    Provider((ref) => ref.watch(core_torrent.resumeTorrentUseCaseProvider));

/// Provider for CancelTorrentUseCase instance.
final cancelTorrentUseCaseProvider =
    Provider((ref) => ref.watch(core_torrent.cancelTorrentUseCaseProvider));
