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

import 'package:jabook/core/domain/downloads/entities/download_item.dart';
import 'package:jabook/core/domain/downloads/repositories/downloads_repository.dart';

/// Use case for getting all downloads.
class GetDownloadsUseCase {
  /// Creates a new GetDownloadsUseCase instance.
  GetDownloadsUseCase(this._repository);

  final DownloadsRepository _repository;

  /// Executes the get downloads use case.
  ///
  /// Returns a list of all downloads, including active and restored downloads.
  ///
  /// Throws [Exception] if getting downloads fails.
  Future<List<DownloadItem>> call() => _repository.getDownloads();
}
