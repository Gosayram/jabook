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

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Handles Cloudflare challenge detection and waiting.
class WebViewCloudflareHandler {
  /// Creates a new WebViewCloudflareHandler instance.
  WebViewCloudflareHandler({
    required this.context,
    required this.isMounted,
    required this.setCloudflareDetected,
  });

  /// The build context for showing UI elements.
  final BuildContext context;

  /// Function to check if the widget is still mounted.
  final bool Function() isMounted;

  /// Function to set the Cloudflare detected state.
  final void Function(bool) setCloudflareDetected;

  /// Timer for periodic Cloudflare challenge checks.
  Timer? cloudflareCheckTimer;

  /// Timestamp when Cloudflare challenge was first detected.
  DateTime? cloudflareChallengeStartTime;

  /// Maximum duration to wait for Cloudflare challenge to complete.
  static const Duration cloudflareWaitDuration = Duration(seconds: 10);

  /// Checks if HTML looks like an active Cloudflare challenge page.
  ///
  /// Returns true only if it's actually a challenge page, not just a page
  /// that mentions Cloudflare (e.g., in headers or meta tags).
  static bool looksLikeCloudflare(String html) {
    final h = html.toLowerCase();

    // Check for active challenge indicators (strong signals)
    final hasActiveChallenge = h.contains('checking your browser') ||
        h.contains('please enable javascript') ||
        h.contains('attention required') ||
        h.contains('cf-chl-bypass') ||
        h.contains('just a moment') ||
        h.contains('verifying you are human') ||
        h.contains('security check') ||
        h.contains('cf-browser-verification') ||
        h.contains('cf-challenge-running') ||
        h.contains('ddos-guard');

    if (!hasActiveChallenge) {
      return false;
    }

    // If we have active challenge indicators, check if page has real content
    final hasRealContent = h.contains('forum') ||
        h.contains('трекер') ||
        h.contains('torrent') ||
        h.contains('login') ||
        h.contains('поиск') ||
        h.contains('search') ||
        h.contains('profile') ||
        h.contains('index.php') ||
        h.contains('viewtopic') ||
        h.contains('viewforum');

    if (hasRealContent) {
      return false;
    }

    // Check page size - challenge pages are usually small (< 50KB)
    if (html.length > 50000) {
      return false;
    }

    return true;
  }

  /// Starts waiting for Cloudflare challenge to complete.
  void startCloudflareWait(InAppWebViewController controller, String url) {
    // Cancel any existing timer
    cloudflareCheckTimer?.cancel();

    // Record challenge start time
    cloudflareChallengeStartTime = DateTime.now();

    // Wait 2 seconds first, then check periodically
    Future.delayed(const Duration(seconds: 2), () {
      if (!isMounted()) return;

      cloudflareCheckTimer =
          Timer.periodic(const Duration(seconds: 10), (timer) async {
        if (!isMounted()) {
          timer.cancel();
          return;
        }

        final elapsed =
            DateTime.now().difference(cloudflareChallengeStartTime!);

        // Check if we've waited too long
        if (elapsed > cloudflareWaitDuration) {
          timer.cancel();
          if (isMounted()) {
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'webview',
              message: 'CloudFlare wait timeout, reloading page',
              context: 'webview_cloudflare',
              extra: {
                'wait_duration_ms': elapsed.inMilliseconds,
                'url': url,
              },
            );
            try {
              await controller.reload();
            } on Exception catch (e) {
              debugPrint('Failed to reload after Cloudflare wait: $e');
            }
          }
          return;
        }

        // Check if challenge has passed
        try {
          final html = await controller.getHtml();
          if (html != null && !looksLikeCloudflare(html)) {
            // Challenge passed! Reload to get actual content
            timer.cancel();
            if (isMounted()) {
              await StructuredLogger().log(
                level: 'info',
                subsystem: 'webview',
                message: 'CloudFlare challenge completed, reloading page',
                context: 'webview_cloudflare',
                extra: {
                  'wait_duration_ms': elapsed.inMilliseconds,
                  'url': url,
                },
              );
              hideCloudflareOverlay();
              await Future.delayed(const Duration(milliseconds: 500));
              await controller.reload();
            }
          }
        } on Exception {
          // Ignore errors during check, continue waiting
        }
      });
    });
  }

  /// Shows a hint message about Cloudflare verification in progress.
  void showCloudflareHint() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
            AppLocalizations.of(context)?.securityVerificationInProgress ??
                'Security verification in progress - please wait...'),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  /// Shows the Cloudflare overlay UI.
  void showCloudflareOverlay() {
    setCloudflareDetected(true);
  }

  /// Hides the Cloudflare overlay UI.
  void hideCloudflareOverlay() {
    setCloudflareDetected(false);
  }

  /// Disposes resources and cancels timers.
  void dispose() {
    cloudflareCheckTimer?.cancel();
  }
}
