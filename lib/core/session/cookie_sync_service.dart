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

import 'package:cookie_jar/cookie_jar.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Handles synchronization of cookies between WebView and Dio client.
///
/// DEPRECATED: This service is no longer needed as we use direct HTTP authentication.
/// Methods are kept as no-op for backward compatibility.
///
/// @deprecated WebView synchronization is no longer used
class CookieSyncService {
  /// Creates a new CookieSyncService instance.
  const CookieSyncService();

  /// Synchronizes cookies from WebView storage to Dio cookie jar.
  ///
  /// DEPRECATED: No-op method kept for backward compatibility.
  /// WebView synchronization is no longer needed.
  ///
  /// @deprecated Use SimpleCookieManager instead
  Future<void> syncFromWebViewToDio(CookieJar cookieJar) async {
    // No-op: WebView synchronization is no longer needed
    await StructuredLogger().log(
      level: 'debug',
      subsystem: 'cookie_sync',
      message: 'syncFromWebViewToDio called but is deprecated (no-op)',
      context: 'cookie_sync_deprecated',
    );
  }

  /// Synchronizes cookies from Dio cookie jar to WebView storage.
  ///
  /// DEPRECATED: No-op method kept for backward compatibility.
  /// WebView synchronization is no longer needed.
  ///
  /// @deprecated Use SimpleCookieManager instead
  Future<void> syncFromDioToWebView(CookieJar cookieJar) async {
    // No-op: WebView synchronization is no longer needed
    await StructuredLogger().log(
      level: 'debug',
      subsystem: 'cookie_sync',
      message: 'syncFromDioToWebView called but is deprecated (no-op)',
      context: 'cookie_sync_deprecated',
    );
  }

  /// Performs bidirectional synchronization between WebView and Dio.
  ///
  /// DEPRECATED: No-op method kept for backward compatibility.
  /// WebView synchronization is no longer needed.
  ///
  /// @deprecated Use SimpleCookieManager instead
  Future<void> syncBothWays(CookieJar cookieJar) async {
    // No-op: WebView synchronization is no longer needed
    await StructuredLogger().log(
      level: 'debug',
      subsystem: 'cookie_sync',
      message: 'syncBothWays called but is deprecated (no-op)',
      context: 'cookie_sync_deprecated',
    );
  }
}
