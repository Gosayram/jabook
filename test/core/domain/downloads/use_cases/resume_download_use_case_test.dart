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
import 'package:jabook/core/domain/downloads/use_cases/resume_download_use_case.dart';

import '../test_doubles/test_downloads_repository.dart';

void main() {
  group('ResumeDownloadUseCase', () {
    late TestDownloadsRepository testRepository;
    late ResumeDownloadUseCase useCase;

    setUp(() {
      testRepository = TestDownloadsRepository();
      useCase = ResumeDownloadUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should resume paused download', () async {
      // Arrange
      final download = DownloadItem(
        id: 'download-1',
        name: 'Test Download',
        status: 'paused',
        progress: 50.0,
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        downloadedBytes: 5000000,
        totalBytes: 10000000,
        seeders: 0,
        leechers: 0,
        isActive: false,
      );
      testRepository.addDownload(download);

      // Act
      await useCase('download-1');

      // Assert
      final downloads = await testRepository.getDownloads();
      final resumedDownload = downloads.firstWhere((d) => d.id == 'download-1');
      expect(resumedDownload.status, equals('downloading'));
      expect(resumedDownload.isActive, isTrue);
      expect(resumedDownload.isPaused, isFalse);
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
        status: 'paused',
        progress: 50.0,
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        downloadedBytes: 5000000,
        totalBytes: 10000000,
        seeders: 0,
        leechers: 0,
        isActive: false,
      );
      testRepository
        ..addDownload(download)
        ..shouldFailResume = true;

      // Act & Assert
      expect(
        () => useCase('download-1'),
        throwsException,
      );
    });
  });
}
