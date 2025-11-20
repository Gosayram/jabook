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

import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:jabook/core/logging/structured_logger.dart';

/// Detects successful login in WebView by checking cookies and page content.
class WebViewLoginDetector {
  /// Private constructor to prevent instantiation.
  WebViewLoginDetector._();

  /// Checks if login was successful by verifying session cookies.
  ///
  /// PRIMARY METHOD: Checks cookies via JavaScript (as cookies are set via JavaScript).
  /// Falls back to URL/HTML checks if JavaScript cookies are not available.
  static Future<bool> checkLoginSuccess(InAppWebViewController controller) async {
    // PRIMARY METHOD: Check cookies via JavaScript with retries
    String? jsCookiesString;
    Exception? lastError;
    
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        // Wait a bit before retry (except first attempt)
        if (attempt > 0) {
          await Future.delayed(Duration(milliseconds: 300 * attempt));
        }
        
        final jsCookiesResult = await controller.evaluateJavascript(source: 'document.cookie');
        if (jsCookiesResult != null) {
          jsCookiesString = jsCookiesResult.toString();
          if (jsCookiesString.isNotEmpty) {
            // Success! Break out of retry loop
            break;
          }
        }
      } on MissingPluginException catch (e) {
        lastError = e;
        if (attempt < 2) {
          continue; // Retry
        }
      } on Exception catch (e) {
        lastError = e;
        if (attempt < 2) {
          continue; // Retry
        }
      }
    }
    
    // Process cookies if we got them
    if (jsCookiesString != null && jsCookiesString.isNotEmpty) {
      // Check if JavaScript cookies contain session cookies
      // Key indicators: bb_session, bb_data (these are set ONLY after successful login)
      final hasBbSession = jsCookiesString.contains('bb_session=');
      final hasBbData = jsCookiesString.contains('bb_data=');
      final hasSessionInJs = hasBbSession || hasBbData;
      
      if (hasSessionInJs) {
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'webview',
          message: 'Login success detected via session cookies (JavaScript)',
          extra: {
            'has_bb_session': hasBbSession,
            'has_bb_data': hasBbData,
            'js_cookies_preview': jsCookiesString.length > 200 
                ? '${jsCookiesString.substring(0, 200)}...' 
                : jsCookiesString,
            'js_cookies_length': jsCookiesString.length,
          },
        );
        return true;
      } else {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'webview',
          message: 'JavaScript cookies found but no session cookies (bb_session/bb_data) detected',
          extra: {
            'js_cookies_preview': jsCookiesString.length > 200 
                ? '${jsCookiesString.substring(0, 200)}...' 
                : jsCookiesString,
            'note': 'User may not be logged in yet, or cookies are still being set',
          },
        );
      }
    } else {
      // Failed to get cookies after all retries
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'webview',
        message: 'Failed to get cookies via JavaScript after all retries, falling back to URL/HTML check',
        cause: lastError?.toString() ?? 'Unknown error',
        extra: {
          'retries': 3,
        },
      );
    }

    // Final fallback: URL/HTML check ONLY if JavaScript cookies were found but no session cookies
    try {
      final currentUrl = await controller.getUrl();
      final urlString = currentUrl?.toString().toLowerCase() ?? '';
      final html = await controller.getHtml();

      // Only check URL/HTML if we have some indication that login might have happened
      final urlIndicatesLogin = urlString.contains('profile.php');
      
      // HTML check: look for logout button or profile link
      final htmlIndicatesLogin = html != null &&
          (html.toLowerCase().contains('выход') ||
              html.toLowerCase().contains('logout') ||
              html.toLowerCase().contains('личный кабинет'));

      // Only return true if we have STRONG indicators
      final isLoginSuccess = urlIndicatesLogin || htmlIndicatesLogin;

      if (isLoginSuccess) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'webview',
          message: 'Login success detected via URL/HTML check (fallback)',
          extra: {
            'url': urlString,
            'has_html': html != null,
            'url_indicates_login': urlIndicatesLogin,
            'html_indicates_login': htmlIndicatesLogin,
            'note': 'This is a fallback - primary method is JavaScript cookies',
          },
        );
      }
      
      return isLoginSuccess;
    } on Exception {
      return false;
    }
  }
}

