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

import 'package:jabook/core/domain/downloads/repositories/downloads_repository.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';

/// Use case for getting the progress stream for a download.
class GetDownloadProgressStreamUseCase {
  /// Creates a new GetDownloadProgressStreamUseCase instance.
  GetDownloadProgressStreamUseCase(this._repository);

  final DownloadsRepository _repository;

  /// Executes the get download progress stream use case.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Returns a stream that emits TorrentProgress whenever the progress updates.
  Stream<TorrentProgress> call(String downloadId) =>
      _repository.getProgressStream(downloadId);
}
