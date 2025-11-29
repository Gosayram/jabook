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

import 'dart:async';

import 'package:jabook/core/domain/downloads/entities/download_item.dart';
import 'package:jabook/core/domain/downloads/repositories/downloads_repository.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';

/// Test implementation of DownloadsRepository for testing.
///
/// This class provides a controllable implementation that can be used
/// in tests to simulate different scenarios.
class TestDownloadsRepository implements DownloadsRepository {
  final List<DownloadItem> _downloads = [];
  final Map<String, StreamController<TorrentProgress>> _progressControllers =
      {};
  bool _shouldFailGetDownloads = false;
  bool _shouldFailPause = false;
  bool _shouldFailResume = false;
  bool _shouldFailResumeRestored = false;
  bool _shouldFailRestart = false;
  bool _shouldFailRedownload = false;
  bool _shouldFailRemove = false;
  String? _redownloadNewId;

  @override
  Future<List<DownloadItem>> getDownloads() async {
    if (_shouldFailGetDownloads) {
      throw Exception('Failed to get downloads');
    }
    return List.unmodifiable(_downloads);
  }

  @override
  Future<void> pauseDownload(String downloadId) async {
    if (_shouldFailPause) {
      throw Exception('Failed to pause download');
    }
    final download = _downloads.firstWhere(
      (d) => d.id == downloadId,
      orElse: () => throw Exception('Download not found'),
    );
    final index = _downloads.indexOf(download);
    _downloads[index] = download.copyWith(
      status: 'paused',
      isActive: false,
    );
  }

  @override
  Future<void> resumeDownload(String downloadId) async {
    if (_shouldFailResume) {
      throw Exception('Failed to resume download');
    }
    final download = _downloads.firstWhere(
      (d) => d.id == downloadId,
      orElse: () => throw Exception('Download not found'),
    );
    final index = _downloads.indexOf(download);
    _downloads[index] = download.copyWith(
      status: 'downloading',
      isActive: true,
    );
  }

  @override
  Future<void> resumeRestoredDownload(String downloadId) async {
    if (_shouldFailResumeRestored) {
      throw Exception('Failed to resume restored download');
    }
    final download = _downloads.firstWhere(
      (d) => d.id == downloadId,
      orElse: () => throw Exception('Download not found'),
    );
    final index = _downloads.indexOf(download);
    _downloads[index] = download.copyWith(
      status: 'downloading',
      isActive: true,
    );
  }

  @override
  Future<void> restartDownload(String downloadId) async {
    if (_shouldFailRestart) {
      throw Exception('Failed to restart download');
    }
    final download = _downloads.firstWhere(
      (d) => d.id == downloadId,
      orElse: () => throw Exception('Download not found'),
    );
    final index = _downloads.indexOf(download);
    _downloads[index] = download.copyWith(
      status: 'downloading',
      progress: 0.0,
      downloadedBytes: 0,
      isActive: true,
    );
  }

  @override
  Future<String> redownload(String downloadId) async {
    if (_shouldFailRedownload) {
      throw Exception('Failed to redownload');
    }
    final newId =
        _redownloadNewId ?? 'new-${DateTime.now().millisecondsSinceEpoch}';
    final download = _downloads.firstWhere(
      (d) => d.id == downloadId,
      orElse: () => throw Exception('Download not found'),
    );
    _downloads.add(download.copyWith(
      id: newId,
      status: 'downloading',
      progress: 0.0,
      downloadedBytes: 0,
      isActive: true,
    ));
    return newId;
  }

  @override
  Future<void> removeDownload(String downloadId) async {
    if (_shouldFailRemove) {
      throw Exception('Failed to remove download');
    }
    _downloads.removeWhere((d) => d.id == downloadId);
    unawaited(_progressControllers[downloadId]?.close());
    _progressControllers.remove(downloadId);
  }

  @override
  Stream<TorrentProgress> getProgressStream(String downloadId) {
    if (!_progressControllers.containsKey(downloadId)) {
      _progressControllers[downloadId] = StreamController<TorrentProgress>();
    }
    return _progressControllers[downloadId]!.stream;
  }

  // Test helpers
  set downloads(List<DownloadItem> value) {
    _downloads
      ..clear()
      ..addAll(value);
  }

  void addDownload(DownloadItem download) {
    _downloads.add(download);
  }

  void emitProgress(String downloadId, TorrentProgress progress) {
    _progressControllers[downloadId]?.add(progress);
  }

  set shouldFailGetDownloads(bool value) => _shouldFailGetDownloads = value;

  set shouldFailPause(bool value) => _shouldFailPause = value;

  set shouldFailResume(bool value) => _shouldFailResume = value;

  set shouldFailResumeRestored(bool value) => _shouldFailResumeRestored = value;

  set shouldFailRestart(bool value) => _shouldFailRestart = value;

  set shouldFailRedownload(bool value) => _shouldFailRedownload = value;

  set shouldFailRemove(bool value) => _shouldFailRemove = value;

  set redownloadNewId(String? value) => _redownloadNewId = value;

  void dispose() {
    for (final controller in _progressControllers.values) {
      controller.close();
    }
    _progressControllers.clear();
  }
}
