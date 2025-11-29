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

import 'package:jabook/core/domain/player/repositories/player_repository.dart';

/// Use case for seeking to a specific position.
class SeekUseCase {
  /// Creates a new SeekUseCase instance.
  SeekUseCase(this._repository);

  final PlayerRepository _repository;

  /// Executes the seek use case.
  ///
  /// The [position] parameter is the position in milliseconds.
  ///
  /// Throws [Exception] if seeking fails.
  Future<void> call(int position) => _repository.seekTo(position);
}
