import 'package:cloudflare_turnstile/cloudflare_turnstile.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Service for handling Cloudflare Turnstile challenges.
///
/// This service provides methods to automatically solve Cloudflare Turnstile
/// challenges that may be encountered during authentication with RuTracker.
class CloudflareTurnstileService {
  /// Private constructor for singleton pattern.
  CloudflareTurnstileService._();

  /// Factory constructor to get the singleton instance.
  factory CloudflareTurnstileService() => _instance;

  /// Singleton instance of the CloudflareTurnstileService.
  static final CloudflareTurnstileService _instance = CloudflareTurnstileService._();

  /// Logger instance for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Site key for Cloudflare Turnstile (placeholder - should be configured per domain)
  static const String _defaultSiteKey = '1x00000000000000000000BB';


  /// Attempts to solve a Cloudflare Turnstile challenge automatically.
  ///
  /// Returns a Turnstile token if successful, or null if the challenge
  /// cannot be solved automatically.
  Future<String?> solveTurnstileChallenge() async {
    try {
      await _logger.log(
        level: 'debug',
        subsystem: 'cloudflare',
        message: 'Attempting to solve Cloudflare Turnstile challenge',
      );

      // Initialize invisible Turnstile instance
      final turnstile = CloudflareTurnstile.invisible(
        siteKey: _defaultSiteKey,
      );

      try {
        // Get the Turnstile token
        final token = await turnstile.getToken();
        
        await _logger.log(
          level: 'info',
          subsystem: 'cloudflare',
          message: 'Cloudflare Turnstile challenge solved successfully',
          extra: {'token_length': token?.length},
        );

        return token;
      } on TurnstileException catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'cloudflare',
          message: 'Cloudflare Turnstile challenge failed',
          cause: e.message,
        );
        return null;
      } finally {
        // Ensure proper cleanup
        await turnstile.dispose();
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cloudflare',
        message: 'Unexpected error during Turnstile challenge',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Checks if a Cloudflare Turnstile challenge is present in the response.
  ///
  /// Returns `true` if the response indicates a Turnstile challenge.
  Future<bool> isTurnstileChallengePresent(String htmlContent) async {
    // Check for Turnstile-specific elements in the HTML
    final hasTurnstileScript = htmlContent.contains('challenges.cloudflare.com/turnstile');
    final hasTurnstileIframe = htmlContent.contains('cf-turnstile');
    final hasTurnstileContainer = htmlContent.contains('turnstile-container');

    return hasTurnstileScript || hasTurnstileIframe || hasTurnstileContainer;
  }

  /// Extracts Turnstile challenge parameters from HTML content.
  ///
  /// Returns a map of challenge parameters if found, otherwise null.
  Map<String, String>? extractTurnstileParams(String htmlContent) {
    try {
      final siteKeyMatch = RegExp(r'data-sitekey="([^"]+)"').firstMatch(htmlContent);
      final actionMatch = RegExp(r'data-action="([^"]+)"').firstMatch(htmlContent);
      final themeMatch = RegExp(r'data-theme="([^"]+)"').firstMatch(htmlContent);

      final params = <String, String>{};
      
      if (siteKeyMatch != null) {
        params['sitekey'] = siteKeyMatch.group(1)!;
      }
      if (actionMatch != null) {
        params['action'] = actionMatch.group(1)!;
      }
      if (themeMatch != null) {
        params['theme'] = themeMatch.group(1)!;
      }

      return params.isNotEmpty ? params : null;
    } on Exception catch (_) {
      return null;
    }
  }

  /// Handles Cloudflare Turnstile protection by solving challenges.
  ///
  /// This method can be called when Cloudflare protection is detected
  /// to automatically solve Turnstile challenges.
  Future<Map<String, String>?> handleTurnstileProtection(String htmlContent) async {
    if (!await isTurnstileChallengePresent(htmlContent)) {
      return null;
    }

    final token = await solveTurnstileChallenge();
    if (token == null) {
      throw const NetworkFailure('Cloudflare Turnstile challenge could not be solved');
    }

    // Return challenge solution parameters
    return {
      'cf-turnstile-response': token,
      'cf-turnstile-sitekey': _defaultSiteKey,
    };
  }

  /// Creates Turnstile options for specific RuTracker domains.
  TurnstileOptions getTurnstileOptions() => TurnstileOptions(
        language: 'ru', // Russian language for RuTracker
      );
}