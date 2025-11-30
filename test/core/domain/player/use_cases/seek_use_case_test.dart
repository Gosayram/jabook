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
import 'package:jabook/core/domain/player/entities/player_state.dart';
import 'package:jabook/core/domain/player/use_cases/seek_use_case.dart';

import '../test_doubles/test_player_repository.dart';

void main() {
  group('SeekUseCase', () {
    late TestPlayerRepository testRepository;
    late SeekUseCase useCase;

    setUp(() {
      testRepository = TestPlayerRepository();
      useCase = SeekUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should seek to specified position', () async {
      // Arrange
      const targetPosition = 50000;
      testRepository.setState(const PlayerState(
        isPlaying: true,
        currentPosition: 0,
        duration: 100000,
        currentIndex: 0,
        playbackState: 2,
        playbackSpeed: 1.0,
      ));

      // Act
      await useCase(targetPosition);

      // Assert
      final state = await testRepository.getState();
      expect(state.currentPosition, equals(targetPosition));
      expect(testRepository.lastSeekPosition, equals(targetPosition));
    });

    test('should throw exception when repository fails', () async {
      // Arrange
      testRepository.shouldFailSeek = true;
      const position = 50000;

      // Act & Assert
      expect(
        () => useCase(position),
        throwsException,
      );
    });

    test('should seek to different positions', () async {
      // Arrange
      testRepository.setState(const PlayerState(
        isPlaying: true,
        currentPosition: 0,
        duration: 200000,
        currentIndex: 0,
        playbackState: 2,
        playbackSpeed: 1.0,
      ));

      // Act
      await useCase(100000);
      await useCase(150000);

      // Assert
      final state = await testRepository.getState();
      expect(state.currentPosition, equals(150000));
      expect(testRepository.lastSeekPosition, equals(150000));
    });

    test('should seek to beginning', () async {
      // Arrange
      testRepository.setState(const PlayerState(
        isPlaying: true,
        currentPosition: 50000,
        duration: 100000,
        currentIndex: 0,
        playbackState: 2,
        playbackSpeed: 1.0,
      ));

      // Act
      await useCase(0);

      // Assert
      final state = await testRepository.getState();
      expect(state.currentPosition, equals(0));
    });
  });
}
