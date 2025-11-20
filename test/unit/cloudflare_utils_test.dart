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

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/net/cloudflare_utils.dart';

void main() {
  group('CloudFlareUtils', () {
    test('isCloudFlareProtected detects CloudFlare headers', () {
      final mockResponse = _createMockResponse(
        headers: {
          'cf-ray': '12345-AMS',
          'server': 'cloudflare',
        },
        body: '<html>Normal content</html>',
      );

      expect(CloudFlareUtils.isCloudFlareProtected(mockResponse), isTrue);
    });

    test('isCloudFlareProtected detects CloudFlare challenge content', () {
      final mockResponse = _createMockResponse(
        headers: {},
        body: '''
          <html>
            <div id="cf-challenge">CloudFlare challenge</div>
            <input name="jschl_vc" value="test">
            <input name="pass" value="test">
          </html>
        ''',
      );

      expect(CloudFlareUtils.isCloudFlareProtected(mockResponse), isTrue);
    });

    test('isCloudFlareHtml detects CloudFlare HTML content', () {
      const html = '''
        <html>
          <div>Checking your browser...</div>
          <script src="/cdn-cgi/challenge-platform/h/g/orchestrate/jsch/v1"></script>
        </html>
      ''';

      expect(CloudFlareUtils.isCloudFlareHtml(html), isTrue);
    });

    test('isCloudFlareHtml does not detect normal HTML', () {
      const html = '''
        <html>
          <head><title>Normal Page</title></head>
          <body>Normal content</body>
        </html>
      ''';

      expect(CloudFlareUtils.isCloudFlareHtml(html), isFalse);
    });
  });
}

// Helper function to create a mock Response
Response _createMockResponse({
  required Map<String, dynamic> headers,
  required String body,
  int statusCode = 200,
}) {
  // Convert headers to the proper format
  final convertedHeaders =
      headers.map((key, value) => MapEntry(key, [value.toString()]));

  return Response(
    requestOptions: RequestOptions(path: 'https://example.com'),
    statusCode: statusCode,
    headers: Headers.fromMap(convertedHeaders),
    data: body,
  );
}
