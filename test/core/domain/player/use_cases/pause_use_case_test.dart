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
import 'package:jabook/core/domain/player/use_cases/pause_use_case.dart';

import '../test_doubles/test_player_repository.dart';

void main() {
  group('PauseUseCase', () {
    late TestPlayerRepository testRepository;
    late PauseUseCase useCase;

    setUp(() {
      testRepository = TestPlayerRepository();
      useCase = PauseUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('should pause when called', () async {
      // Arrange
      testRepository.setState(const PlayerState(
        isPlaying: true,
        currentPosition: 50000,
        duration: 100000,
        currentIndex: 0,
        playbackSpeed: 1.0,
        playbackState: 2,
      ));

      // Act
      await useCase();

      // Assert
      final state = await testRepository.getState();
      expect(state.isPlaying, isFalse);
    });

    test('should throw exception when repository fails', () async {
      // Arrange
      testRepository.shouldFailPause = true;

      // Act & Assert
      expect(
        () => useCase(),
        throwsException,
      );
    });

    test('should maintain position when pausing', () async {
      // Arrange
      const position = 75000;
      testRepository.setState(const PlayerState(
        isPlaying: true,
        currentPosition: position,
        duration: 100000,
        currentIndex: 0,
        playbackSpeed: 1.0,
        playbackState: 2,
      ));

      // Act
      await useCase();

      // Assert
      final state = await testRepository.getState();
      expect(state.isPlaying, isFalse);
      expect(state.currentPosition, equals(position));
    });
  });
}
