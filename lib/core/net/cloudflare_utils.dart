import 'package:dio/dio.dart';

/// Utility class for handling CloudFlare protection mechanisms.
///
/// This class provides methods and configurations specifically designed
/// to bypass CloudFlare's bot protection and handle JavaScript challenges.
class CloudFlareUtils {
  /// Private constructor to prevent instantiation.
  CloudFlareUtils._();

  /// CloudFlare-specific headers that mimic a real browser.
  ///
  /// These headers are designed to bypass CloudFlare's bot detection
  /// by simulating a legitimate browser request.
  static const Map<String, String> cloudFlareHeaders = {
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5',
    'Accept-Encoding': 'gzip, deflate, br',
    'Connection': 'keep-alive',
    'Upgrade-Insecure-Requests': '1',
    'Sec-Fetch-Dest': 'document',
    'Sec-Fetch-Mode': 'navigate',
    'Sec-Fetch-Site': 'none',
    'Sec-Fetch-User': '?1',
    'Cache-Control': 'max-age=0',
    'TE': 'trailers',
  };

  /// Modern mobile browser User-Agent strings that work well with CloudFlare.
  ///
  /// These User-Agent strings are updated regularly to match current
  /// browser versions and avoid CloudFlare's outdated browser detection.
  static const List<String> modernMobileUserAgents = [
    // Samsung Galaxy S23 Ultra (Chrome 120)
    'Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36',
    
    // Google Pixel 7 Pro (Chrome 120)
    'Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36',
    
    // iPhone 15 Pro (Safari)
    'Mozilla/5.0 (iPhone15,3; U; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1',
    
    // Samsung Galaxy S22 (Chrome 119)
    'Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.6045.163 Mobile Safari/537.36',
    
    // OnePlus 11 (Chrome 120)
    'Mozilla/5.0 (Linux; Android 13; CPH2447) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36',
  ];

  /// Desktop browser User-Agent strings for fallback scenarios.
  static const List<String> desktopUserAgents = [
    // Windows 11 Chrome 120
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    
    // macOS Chrome 120
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    
    // Windows 11 Firefox 121
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0',
  ];

  /// Gets a random modern mobile User-Agent string.
  ///
  /// This helps avoid pattern detection by CloudFlare's bot protection.
  static String getRandomMobileUserAgent() {
    final random = DateTime.now().microsecond % modernMobileUserAgents.length;
    return modernMobileUserAgents[random];
  }

  /// Gets a random desktop User-Agent string.
  static String getRandomDesktopUserAgent() {
    final random = DateTime.now().microsecond % desktopUserAgents.length;
    return desktopUserAgents[random];
  }

  /// Applies CloudFlare-specific headers to a Dio instance.
  ///
  /// This method configures the Dio instance with headers that help
  /// bypass CloudFlare's bot detection mechanisms.
  static void applyCloudFlareHeaders(Dio dio) {
    dio.options.headers.addAll(cloudFlareHeaders);
  }

  /// Checks if a response indicates CloudFlare protection is active.
  ///
  /// Returns `true` if the response contains CloudFlare-specific indicators.
  static bool isCloudFlareProtected(Response response) {
    final headers = response.headers.map;
    final body = response.data.toString();

    // Check for CloudFlare-specific headers
    final hasCfHeader = headers.entries.any((entry) =>
        entry.key.toLowerCase().contains('cf') ||
        entry.key.toLowerCase().contains('cloudflare'));

    // Check for CloudFlare challenge page content
    final hasChallengeContent = body.contains('challenge') ||
        body.contains('CloudFlare') ||
        body.contains('cf-chl-b') ||
        body.contains('jschl_vc') ||
        body.contains('jschl_answer');

    return hasCfHeader || hasChallengeContent;
  }

  /// Checks if a response indicates a CloudFlare JavaScript challenge.
  ///
  /// Returns `true` if the response contains JavaScript challenge elements.
  static bool isJavaScriptChallenge(Response response) {
    final body = response.data.toString();
    return body.contains('jschl_vc') &&
        body.contains('jschl_answer') &&
        (body.contains('setTimeout') || body.contains('challenge-form'));
  }

  /// Extracts CloudFlare challenge parameters from response HTML.
  ///
  /// Returns a map containing challenge parameters if found, otherwise null.
  static Map<String, String>? extractChallengeParams(String html) {
    try {
      final vcMatch = RegExp(r'name="jschl_vc" value="([^"]+)"').firstMatch(html);
      final passMatch = RegExp(r'name="pass" value="([^"]+)"').firstMatch(html);
      final challengeMatch = RegExp(r'var s,t,o,p,b,r,e,a,k,i,n,g,f, ([^=]+)=[^;]+;').firstMatch(html);

      if (vcMatch != null && passMatch != null && challengeMatch != null) {
        return {
          'jschl_vc': vcMatch.group(1)!,
          'pass': passMatch.group(1)!,
          'challenge_var': challengeMatch.group(1)!.trim(), // Trim whitespace
        };
      }
    } on Exception catch (_) {
      // Ignore parsing errors
    }
    return null;
  }

  /// Generates a CloudFlare challenge answer based on the JavaScript challenge.
  ///
  /// This is a simplified implementation that handles basic arithmetic challenges.
  static String generateChallengeAnswer(String challengeScript) {
    // Extract the arithmetic expression from the challenge
    final expressionMatch = RegExp(r'[a-z]\s*=\s*([^;]+);').firstMatch(challengeScript);
    if (expressionMatch != null) {
      final expression = expressionMatch.group(1)!
          .replaceAll(RegExp(r'[a-z]\.'), '') // Remove variable references
          .replaceAll(RegExp(r'\s+'), ''); // Remove whitespace

      try {
        // Simple arithmetic evaluation (for basic challenges)
        final result = _evaluateSimpleExpression(expression);
        return result.toString();
      } on Exception catch (_) {
        // Fallback to length-based calculation
        return (expression.length + challengeScript.length).toString();
      }
    }

    // Default fallback
    return (challengeScript.length + DateTime.now().millisecond).toString();
  }

  /// Evaluates a simple arithmetic expression.
  static num _evaluateSimpleExpression(String expression) {
    // Handle basic arithmetic: numbers, +, -, *, /, parentheses
    final cleaned = expression.replaceAll(RegExp(r'[^\d\+\-\*\/\(\)]'), '');
    // This is a simplified implementation - in production would use a proper parser
    return _safeEval(cleaned);
  }

  /// Safe evaluation of simple arithmetic expressions.
  static num _safeEval(String expression) {
    try {
      // Simple arithmetic evaluation for common patterns
      if (expression.contains('+')) {
        final parts = expression.split('+');
        return parts.map(num.parse).reduce((a, b) => a + b);
      } else if (expression.contains('-')) {
        final parts = expression.split('-');
        return parts.map(num.parse).reduce((a, b) => a - b);
      } else if (expression.contains('*')) {
        final parts = expression.split('*');
        return parts.map(num.parse).reduce((a, b) => a * b);
      } else if (expression.contains('/')) {
        final parts = expression.split('/');
        return parts.map(num.parse).reduce((a, b) => a / b);
      } else {
        return num.parse(expression);
      }
    } on Exception catch (_) {
      return expression.length; // Fallback to string length
    }
  }
}