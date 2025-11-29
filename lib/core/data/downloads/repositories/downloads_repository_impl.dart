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

import 'package:jabook/core/data/downloads/datasources/downloads_local_datasource.dart';
import 'package:jabook/core/data/downloads/mappers/downloads_mapper.dart';
import 'package:jabook/core/data/torrent/mappers/torrent_mapper.dart';
import 'package:jabook/core/domain/downloads/entities/download_item.dart';
import 'package:jabook/core/domain/downloads/repositories/downloads_repository.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';

/// Implementation of DownloadsRepository using data sources.
class DownloadsRepositoryImpl implements DownloadsRepository {
  /// Creates a new DownloadsRepositoryImpl instance.
  DownloadsRepositoryImpl(this._localDataSource);

  final DownloadsLocalDataSource _localDataSource;

  @override
  Future<List<DownloadItem>> getDownloads() async {
    final data = await _localDataSource.getDownloads();
    return DownloadsMapper.toDomainList(data);
  }

  @override
  Future<void> pauseDownload(String downloadId) =>
      _localDataSource.pauseDownload(downloadId);

  @override
  Future<void> resumeDownload(String downloadId) =>
      _localDataSource.resumeDownload(downloadId);

  @override
  Future<void> resumeRestoredDownload(String downloadId) =>
      _localDataSource.resumeRestoredDownload(downloadId);

  @override
  Future<void> restartDownload(String downloadId) =>
      _localDataSource.restartDownload(downloadId);

  @override
  Future<String> redownload(String downloadId) =>
      _localDataSource.redownload(downloadId);

  @override
  Future<void> removeDownload(String downloadId) =>
      _localDataSource.removeDownload(downloadId);

  @override
  Stream<TorrentProgress> getProgressStream(String downloadId) =>
      _localDataSource
          .getProgressStream(downloadId)
          .map(TorrentMapper.toDomain);
}
