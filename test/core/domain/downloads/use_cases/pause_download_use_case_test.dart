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

import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/domain/downloads/entities/download_item.dart';
import 'package:jabook/core/domain/downloads/use_cases/pause_download_use_case.dart';

import '../test_doubles/test_downloads_repository.dart';

void main() {
  group('PauseDownloadUseCase', () {
    late TestDownloadsRepository testRepository;
    late PauseDownloadUseCase useCase;

    setUp(() {
      testRepository = TestDownloadsRepository();
      useCase = PauseDownloadUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should pause active download', () async {
      // Arrange
      final download = DownloadItem(
        id: 'download-1',
        name: 'Test Download',
        status: 'downloading',
        progress: 50.0,
        downloadSpeed: 1000.0,
        uploadSpeed: 100.0,
        downloadedBytes: 5000000,
        totalBytes: 10000000,
        seeders: 5,
        leechers: 10,
        isActive: true,
      );
      testRepository.addDownload(download);

      // Act
      await useCase('download-1');

      // Assert
      final downloads = await testRepository.getDownloads();
      final pausedDownload = downloads.firstWhere((d) => d.id == 'download-1');
      expect(pausedDownload.status, equals('paused'));
      expect(pausedDownload.isActive, isFalse);
      expect(pausedDownload.isPaused, isTrue);
    });

    test('should throw exception when download not found', () async {
      // Act & Assert
      expect(
        () => useCase('non-existent'),
        throwsException,
      );
    });

    test('should throw exception when repository fails', () async {
      // Arrange
      final download = DownloadItem(
        id: 'download-1',
        name: 'Test Download',
        status: 'downloading',
        progress: 50.0,
        downloadSpeed: 1000.0,
        uploadSpeed: 100.0,
        downloadedBytes: 5000000,
        totalBytes: 10000000,
        seeders: 5,
        leechers: 10,
        isActive: true,
      );
      testRepository
        ..addDownload(download)
        ..shouldFailPause = true;

      // Act & Assert
      expect(
        () => useCase('download-1'),
        throwsException,
      );
    });
  });
}
