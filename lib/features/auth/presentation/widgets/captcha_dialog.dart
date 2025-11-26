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

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:jabook/core/animations/dialog_utils.dart';
import 'package:jabook/core/auth/captcha_detector.dart';
import 'package:jabook/core/utils/responsive_utils.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Universal dialog for displaying and solving captcha challenges.
///
/// Supports both RuTracker captcha (image-based) and CloudFlare challenges.
/// Adapts its size based on the captcha type.
class CaptchaDialog extends StatefulWidget {
  /// Creates a new CaptchaDialog instance.
  const CaptchaDialog({
    required this.captchaType,
    this.rutrackerCaptchaData,
    this.captchaUrl,
    super.key,
  });

  /// Type of captcha to display.
  final CaptchaType captchaType;

  /// RuTracker captcha data (if type is rutracker).
  final RutrackerCaptchaData? rutrackerCaptchaData;

  /// URL for CloudFlare challenge (if type is cloudflare).
  final String? captchaUrl;

  /// Shows the captcha dialog and returns the solved captcha code or null.
  static Future<String?> show(
    BuildContext context, {
    required CaptchaType captchaType,
    RutrackerCaptchaData? rutrackerCaptchaData,
    String? captchaUrl,
  }) async =>
      DialogUtils.showAnimatedDialog<String>(
        context: context,
        barrierDismissible: false,
        builder: (context) => CaptchaDialog(
          captchaType: captchaType,
          rutrackerCaptchaData: rutrackerCaptchaData,
          captchaUrl: captchaUrl,
        ),
      );

  @override
  State<CaptchaDialog> createState() => _CaptchaDialogState();
}

class _CaptchaDialogState extends State<CaptchaDialog> {
  final _captchaCodeController = TextEditingController();
  final bool _isLoading = false;

  @override
  void dispose() {
    _captchaCodeController.dispose();
    super.dispose();
  }

  void _submit() {
    final code = _captchaCodeController.text.trim();
    if (code.isNotEmpty) {
      Navigator.of(context).pop(code);
    }
  }

  void _cancel() {
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final isRutracker = widget.captchaType == CaptchaType.rutracker;

    return Dialog(
      child: Container(
        width: _getDialogWidth(context),
        height: _getDialogHeight(context),
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Title
            Text(
              localizations?.captchaVerificationRequired ??
                  'Captcha verification required',
              style: Theme.of(context).textTheme.titleLarge,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),

            // Captcha content
            if (isRutracker && widget.rutrackerCaptchaData != null)
              _buildRutrackerCaptcha(widget.rutrackerCaptchaData!)
            else if (widget.captchaType == CaptchaType.cloudflare)
              _buildCloudflareCaptcha()
            else
              _buildUnknownCaptcha(),

            const SizedBox(height: 16),

            // Input field for RuTracker captcha
            if (isRutracker)
              TextField(
                controller: _captchaCodeController,
                autofocus: true,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 2,
                ),
                decoration: const InputDecoration(
                  labelText: 'Enter code',
                  hintText: 'Code',
                  border: OutlineInputBorder(),
                ),
                onSubmitted: (_) => _submit(),
              ),

            const SizedBox(height: 16),

            // Buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: _isLoading ? null : _cancel,
                  child: Text(localizations?.cancel ?? 'Cancel'),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _isLoading || !isRutracker
                      ? null
                      : (_captchaCodeController.text.trim().isEmpty
                          ? null
                          : _submit),
                  child: _isLoading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Submit'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRutrackerCaptcha(RutrackerCaptchaData data) => Column(
        children: [
          // Captcha image
          Container(
            padding: const EdgeInsets.all(8.0),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey.shade300),
              borderRadius: BorderRadius.circular(8),
            ),
            child: CachedNetworkImage(
              imageUrl: data.captchaImageUrl,
              width: 120,
              height: 72,
              fit: BoxFit.contain,
              placeholder: (context, url) => const Center(
                child: CircularProgressIndicator(),
              ),
              errorWidget: (context, url, error) => const Icon(Icons.error),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Enter the code shown in the image above',
            style: Theme.of(context).textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
        ],
      );

  Widget _buildCloudflareCaptcha() => Expanded(
        child: Container(
          padding: const EdgeInsets.all(8.0),
          decoration: BoxDecoration(
            border: Border.all(color: Colors.grey.shade300),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Center(
            child: Text(
              'CloudFlare challenge detected. Please use WebView to complete the challenge.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      );

  Widget _buildUnknownCaptcha() => Container(
        padding: const EdgeInsets.all(16.0),
        child: const Text(
          'Unknown captcha type detected. Please use WebView to complete the login.',
          textAlign: TextAlign.center,
        ),
      );

  double _getDialogWidth(BuildContext context) {
    switch (widget.captchaType) {
      case CaptchaType.rutracker:
        // Use responsive width, but ensure it's not too wide on small screens
        final responsiveWidth = ResponsiveUtils.getDialogWidth(context);
        if (responsiveWidth != null) {
          // Use responsive width if available, but cap at 400px for compact captcha
          return responsiveWidth < 400 ? responsiveWidth : 400;
        }
        // For mobile (full width), use 90% of screen width but max 400px
        final screenWidth = MediaQuery.of(context).size.width;
        final calculatedWidth = screenWidth * 0.9;
        return calculatedWidth < 400 ? calculatedWidth : 400;
      case CaptchaType.cloudflare:
        // Wide for CloudFlare - use 90% of screen width
        return MediaQuery.of(context).size.width * 0.9;
      case CaptchaType.unknown:
        // Use responsive width for unknown captcha type
        final responsiveWidth = ResponsiveUtils.getDialogWidth(context);
        if (responsiveWidth != null) {
          return responsiveWidth < 400 ? responsiveWidth : 400;
        }
        final screenWidth = MediaQuery.of(context).size.width;
        return screenWidth * 0.9;
    }
  }

  double? _getDialogHeight(BuildContext context) {
    switch (widget.captchaType) {
      case CaptchaType.rutracker:
        // Compact height for RuTracker captcha
        return ResponsiveUtils.getDialogHeight(context, contentType: 'compact');
      case CaptchaType.cloudflare:
        // Tall for CloudFlare - use 70% of screen height
        return MediaQuery.of(context).size.height * 0.7;
      case CaptchaType.unknown:
        // Normal height for unknown captcha
        return ResponsiveUtils.getDialogHeight(context);
    }
  }
}
