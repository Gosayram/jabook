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
import 'package:jabook/core/domain/downloads/use_cases/get_downloads_use_case.dart';

import '../test_doubles/test_downloads_repository.dart';

void main() {
  group('GetDownloadsUseCase', () {
    late TestDownloadsRepository testRepository;
    late GetDownloadsUseCase useCase;

    setUp(() {
      testRepository = TestDownloadsRepository();
      useCase = GetDownloadsUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should return list of downloads', () async {
      // Arrange
      final downloads = [
        DownloadItem(
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
        ),
        DownloadItem(
          id: 'download-2',
          name: 'Test Download 2',
          status: 'paused',
          progress: 30.0,
          downloadSpeed: 0.0,
          uploadSpeed: 0.0,
          downloadedBytes: 3000000,
          totalBytes: 10000000,
          seeders: 0,
          leechers: 0,
          isActive: false,
        ),
      ];
      testRepository.downloads = downloads;

      // Act
      final result = await useCase();

      // Assert
      expect(result, equals(downloads));
      expect(result.length, equals(2));
      expect(result[0].id, equals('download-1'));
      expect(result[1].id, equals('download-2'));
    });

    test('should return empty list when no downloads', () async {
      // Arrange
      testRepository.downloads = [];

      // Act
      final result = await useCase();

      // Assert
      expect(result, isEmpty);
    });

    test('should throw exception when repository fails', () async {
      // Arrange
      testRepository.shouldFailGetDownloads = true;

      // Act & Assert
      expect(
        () => useCase(),
        throwsException,
      );
    });

    test('should return restored downloads', () async {
      // Arrange
      final downloads = [
        DownloadItem(
          id: 'download-1',
          name: 'Restored Download',
          status: 'restored',
          progress: 25.0,
          downloadSpeed: 0.0,
          uploadSpeed: 0.0,
          downloadedBytes: 2500000,
          totalBytes: 10000000,
          seeders: 0,
          leechers: 0,
          isActive: false,
        ),
      ];
      testRepository.downloads = downloads;

      // Act
      final result = await useCase();

      // Assert
      expect(result.length, equals(1));
      expect(result[0].isRestored, isTrue);
      expect(result[0].isActive, isFalse);
    });
  });
}
