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
import 'package:jabook/core/net/user_agent_manager.dart';

void main() {
  // Initialize Flutter binding for tests that need platform services
  TestWidgetsFlutterBinding.ensureInitialized();

  group('UserAgentManager', () {
    test('getUserAgent returns valid User-Agent', () async {
      final manager = UserAgentManager();
      final userAgent = await manager.getUserAgent();

      expect(userAgent, isNotNull);
      expect(userAgent, isNotEmpty);
      expect(userAgent.contains('Mozilla/5.0'), isTrue);
    });

    test('getUserAgent with forceRefresh returns valid User-Agent', () async {
      final manager = UserAgentManager();
      final userAgent = await manager.getUserAgent(forceRefresh: true);

      expect(userAgent, isNotNull);
      expect(userAgent, isNotEmpty);
      expect(userAgent.contains('Mozilla/5.0'), isTrue);
    });

    test('clearUserAgent works without errors', () async {
      final manager = UserAgentManager();
      await expectLater(manager.clearUserAgent(), completes);
    });

    test('refreshUserAgent works without errors', () async {
      final manager = UserAgentManager();
      await expectLater(manager.refreshUserAgent(), completes);
    });

    test('applyUserAgentToDio works with mock Dio', () async {
      final manager = UserAgentManager();
      final mockDio = _MockDio();

      await manager.applyUserAgentToDio(mockDio);

      expect(mockDio.options.headers.containsKey('User-Agent'), isTrue);
      expect(mockDio.options.headers['User-Agent'], isNotEmpty);
    });
  });
}

// Mock Dio class for testing
class _MockDio {
  final Map<String, dynamic> headers = {};
  final _MockOptions options = _MockOptions();

  Future<void> applyUserAgentToDio(_MockDio dio) async {
    // Mock implementation
  }
}

class _MockOptions {
  final Map<String, dynamic> headers = {};
}
