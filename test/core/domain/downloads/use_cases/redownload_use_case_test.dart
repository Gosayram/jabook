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
import 'package:jabook/core/domain/downloads/use_cases/redownload_use_case.dart';

import '../test_doubles/test_downloads_repository.dart';

void main() {
  group('RedownloadUseCase', () {
    late TestDownloadsRepository testRepository;
    late RedownloadUseCase useCase;

    setUp(() {
      testRepository = TestDownloadsRepository();
      useCase = RedownloadUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should redownload and return new download ID', () async {
      // Arrange
      final download = DownloadItem(
        id: 'download-1',
        name: 'Test Download',
        status: 'completed',
        progress: 100.0,
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        downloadedBytes: 10000000,
        totalBytes: 10000000,
        seeders: 0,
        leechers: 0,
        isActive: false,
      );
      testRepository
        ..addDownload(download)
        ..redownloadNewId = 'download-2';

      // Act
      final newId = await useCase('download-1');

      // Assert
      expect(newId, equals('download-2'));
      final downloads = await testRepository.getDownloads();
      expect(downloads.length, equals(2));
      final newDownload = downloads.firstWhere((d) => d.id == 'download-2');
      expect(newDownload.status, equals('downloading'));
      expect(newDownload.progress, equals(0.0));
      expect(newDownload.downloadedBytes, equals(0));
      expect(newDownload.isActive, isTrue);
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
        status: 'completed',
        progress: 100.0,
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        downloadedBytes: 10000000,
        totalBytes: 10000000,
        seeders: 0,
        leechers: 0,
        isActive: false,
      );
      testRepository
        ..addDownload(download)
        ..shouldFailRedownload = true;

      // Act & Assert
      expect(
        () => useCase('download-1'),
        throwsException,
      );
    });
  });
}
