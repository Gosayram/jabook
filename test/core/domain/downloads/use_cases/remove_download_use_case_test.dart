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
import 'package:jabook/core/domain/downloads/use_cases/remove_download_use_case.dart';

import '../test_doubles/test_downloads_repository.dart';

void main() {
  group('RemoveDownloadUseCase', () {
    late TestDownloadsRepository testRepository;
    late RemoveDownloadUseCase useCase;

    setUp(() {
      testRepository = TestDownloadsRepository();
      useCase = RemoveDownloadUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should remove download', () async {
      // Arrange
      final download1 = DownloadItem(
        id: 'download-1',
        name: 'Test Download 1',
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
      final download2 = DownloadItem(
        id: 'download-2',
        name: 'Test Download 2',
        status: 'downloading',
        progress: 30.0,
        downloadSpeed: 800.0,
        uploadSpeed: 80.0,
        downloadedBytes: 3000000,
        totalBytes: 10000000,
        seeders: 3,
        leechers: 8,
        isActive: true,
      );
      testRepository
        ..addDownload(download1)
        ..addDownload(download2);

      // Act
      await useCase('download-1');

      // Assert
      final downloads = await testRepository.getDownloads();
      expect(downloads.length, equals(1));
      expect(downloads[0].id, equals('download-2'));
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
        ..shouldFailRemove = true;

      // Act & Assert
      expect(
        () => useCase('download-1'),
        throwsException,
      );
    });
  });
}
