import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/net/cloudflare_utils.dart';

void main() {
  group('CloudFlareUtils', () {
    test('getRandomMobileUserAgent returns valid User-Agent', () {
      final userAgent = CloudFlareUtils.getRandomMobileUserAgent();
      expect(userAgent, isNotNull);
      expect(userAgent, isNotEmpty);
      expect(userAgent.contains('Mozilla/5.0'), isTrue);
    });

    test('getRandomDesktopUserAgent returns valid User-Agent', () {
      final userAgent = CloudFlareUtils.getRandomDesktopUserAgent();
      expect(userAgent, isNotNull);
      expect(userAgent, isNotEmpty);
      expect(userAgent.contains('Mozilla/5.0'), isTrue);
    });

    test('cloudFlareHeaders contains required headers', () {
      const headers = CloudFlareUtils.cloudFlareHeaders;
      expect(headers, isNotNull);
      expect(headers.isNotEmpty, isTrue);
      expect(headers.containsKey('Accept'), isTrue);
      expect(headers.containsKey('Accept-Language'), isTrue);
      expect(headers.containsKey('User-Agent'), isFalse); // Should be applied separately
    });

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

    test('isJavaScriptChallenge detects JavaScript challenge', () {
      final mockResponse = _createMockResponse(
        headers: {},
        body: '''
          <html>
            <input name="jschl_vc" value="test">
            <input name="jschl_answer" value="test">
            <input name="pass" value="test">
            <script>setTimeout(function(){}, 4000);</script>
          </html>
        ''',
      );

      expect(CloudFlareUtils.isJavaScriptChallenge(mockResponse), isTrue);
    });

    test('extractChallengeParams extracts valid parameters', () {
      const html = '''
        <input name="jschl_vc" value="test_vc">
        <input name="pass" value="test_pass">
        <script>var s,t,o,p,b,r,e,a,k,i,n,g,f, abc = 123;</script>
      ''';

      final params = CloudFlareUtils.extractChallengeParams(html);
      expect(params, isNotNull);
      expect(params!['jschl_vc'], 'test_vc');
      expect(params['pass'], 'test_pass');
      expect(params['challenge_var'], 'abc');
    });

    test('generateChallengeAnswer handles basic arithmetic', () {
      const challengeScript = 'var a = 1 + 2 * 3;';
      final answer = CloudFlareUtils.generateChallengeAnswer(challengeScript);
      expect(answer, isNotNull);
      expect(answer, isNotEmpty);
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
  final convertedHeaders = headers.map((key, value) =>
    MapEntry(key, [value.toString()])
  );
  
  return Response(
    requestOptions: RequestOptions(path: 'https://example.com'),
    statusCode: statusCode,
    headers: Headers.fromMap(convertedHeaders),
    data: body,
  );
}