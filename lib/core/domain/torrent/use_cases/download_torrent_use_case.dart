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

import 'dart:typed_data';

import 'package:jabook/core/domain/torrent/repositories/torrent_repository.dart';

/// Use case for starting a torrent download.
class DownloadTorrentUseCase {
  /// Creates a new DownloadTorrentUseCase instance.
  DownloadTorrentUseCase(this._repository);

  final TorrentRepository _repository;

  /// Executes the download torrent use case.
  ///
  /// The [torrentBytes] parameter contains the content of the .torrent file (optional).
  /// The [savePath] parameter is the directory where files will be saved.
  /// The [magnetUrl] parameter is the magnet URL for the torrent (required if torrentBytes is null).
  /// The [metadata] parameter contains optional metadata (title, author, etc.).
  ///
  /// Returns the task ID for the new download.
  ///
  /// Throws [Exception] if starting the download fails.
  Future<String> call(
    String savePath, {
    Uint8List? torrentBytes,
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  }) =>
      _repository.startDownload(
        torrentBytes,
        savePath,
        magnetUrl: magnetUrl,
        metadata: metadata,
        sequential: sequential,
        selectedFileIndices: selectedFileIndices,
      );
}
