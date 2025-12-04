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

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/auth/captcha_detector.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/auth/presentation/widgets/captcha_dialog.dart';
import 'package:jabook/features/webview/secure_rutracker_webview.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for RuTracker authentication and connection management.
///
/// This screen allows users to login to RuTracker, check connection status,
/// and manage authentication settings before accessing content.
class AuthScreen extends ConsumerStatefulWidget {
  /// Creates a new AuthScreen instance.
  const AuthScreen({super.key});

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _rememberMe = true;
  String _statusMessage = '';
  Color _statusColor = Colors.grey;
  bool _isLoggingIn = false;

  @override
  void initState() {
    super.initState();
    // Log that AuthScreen is being initialized
    safeUnawaited(
      StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'AuthScreen initState called',
        context: 'auth_screen_init',
      ),
    );
    // Initial status check - do it asynchronously after first frame
    // Don't use ref.read() here as it can hang if provider is in error state
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // Use safeUnawaited to prevent blocking
      safeUnawaited(
        _refreshAuthStatusAsync(),
        onError: (e, stack) {
          // Log error but don't block UI
          StructuredLogger().log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to refresh auth status in initState',
            cause: e.toString(),
          );
        },
      );
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Check auth status when screen is shown (async, don't block)
    // Use safeUnawaited to prevent blocking the UI
    safeUnawaited(
      _refreshAuthStatusAsync(),
      onError: (e, stack) {
        // Log error but don't block UI
        StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Failed to refresh auth status in didChangeDependencies',
          cause: e.toString(),
        );
      },
    );
  }

  /// Refreshes auth status asynchronously without blocking UI
  Future<void> _refreshAuthStatusAsync() async {
    try {
      final repository = ref.read(authRepositoryProvider);
      await repository.refreshAuthStatus();
    } on Exception {
      // Ignore errors - we'll show default state
      // This prevents UI blocking if provider is in error state
    }
  }

  Future<void> _login() async {
    if (_usernameController.text.isEmpty || _passwordController.text.isEmpty) {
      setState(() {
        _statusMessage = AppLocalizations.of(context)?.pleaseEnterCredentials ??
            'Please enter username and password';
        _statusColor = Colors.red;
        _isLoggingIn = false;
      });
      return;
    }

    setState(() {
      _statusMessage =
          AppLocalizations.of(context)?.loggingInText ?? 'Logging in...';
      _statusColor = Colors.blue;
      _isLoggingIn = true;
    });

    try {
      final repository = ref.read(authRepositoryProvider);
      final success = await repository.login(
        _usernameController.text,
        _passwordController.text,
      );

      if (success && _rememberMe) {
        await repository.saveCredentials(
          username: _usernameController.text,
          password: _passwordController.text,
          rememberMe: _rememberMe,
        );
      }

      if (mounted) {
        setState(() {
          _statusMessage = success
              ? (AppLocalizations.of(context)?.loginSuccessMessage ??
                  'Login successful!')
              : (AppLocalizations.of(context)?.loginFailedMessage ??
                  'Login failed. Please check credentials');
          _statusColor = success ? Colors.green : Colors.red;
          _isLoggingIn = false;
        });

        if (success) {
          await _testConnection();

          // CRITICAL: Refresh auth status and invalidate providers to update cache
          // This ensures all providers are updated after successful login
          final repository = ref.read(authRepositoryProvider);
          await repository.refreshAuthStatus();
          ref.invalidate(isLoggedInProvider);

          // Update access level to full access after successful login
          ref.read(accessProvider.notifier).upgradeToFullAccess();
        }
      }
    } on AuthFailure catch (e) {
      if (!mounted) return;

      // Check if this is a captcha required error
      if (e.captchaType == null) {
        // Not a captcha error, handle as regular error
        setState(() {
          _statusMessage = e.message;
          _statusColor = Colors.red;
          _isLoggingIn = false;
        });
        return;
      }

      final captchaType = e.captchaType;
      final rutrackerCaptchaData = e.rutrackerCaptchaData;

      setState(() {
        _statusMessage =
            AppLocalizations.of(context)?.captchaVerificationRequired ??
                'Captcha verification required';
        _statusColor = Colors.orange;
        _isLoggingIn = false;
      });

      await _showCaptchaDialog(
        context,
        captchaType: captchaType,
        rutrackerCaptchaData: rutrackerCaptchaData,
      );
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _statusMessage = AppLocalizations.of(context)
                  ?.loginFailedWithError(e.toString()) ??
              'Login failed: ${e.toString()}';
          _statusColor = Colors.red;
          _isLoggingIn = false;
        });
      }
    }
  }

  /// Shows a dialog for captcha verification.
  ///
  /// If [captchaType] and [rutrackerCaptchaData] are provided, shows the
  /// universal CaptchaDialog. Otherwise, shows a dialog offering to use WebView.
  Future<void> _showCaptchaDialog(
    BuildContext context, {
    CaptchaType? captchaType,
    RutrackerCaptchaData? rutrackerCaptchaData,
  }) async {
    if (!mounted) return;

    // Store Navigator before async operations to avoid BuildContext issues
    final navigator = Navigator.of(context);

    // If we have RuTracker captcha data, show the universal captcha dialog
    if (captchaType == CaptchaType.rutracker && rutrackerCaptchaData != null) {
      final captchaCode = await CaptchaDialog.show(
        context,
        captchaType: CaptchaType.rutracker,
        rutrackerCaptchaData: rutrackerCaptchaData,
      );

      if (captchaCode != null && captchaCode.isNotEmpty && mounted) {
        // Retry login with captcha code
        await _loginWithCaptcha(captchaCode, rutrackerCaptchaData);
      }
      return;
    }

    // For CloudFlare or unknown captcha, or if no data available, show WebView option
    final localizations = AppLocalizations.of(context);
    final useWebView = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          localizations?.captchaVerificationRequired ??
              'Captcha verification required',
        ),
        content: const Text(
          'RuTracker requires captcha verification. You can use WebView to complete the login with captcha.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(
              localizations?.loginViaWebViewButton ?? 'Login via WebView',
            ),
          ),
        ],
      ),
    );

    if ((useWebView ?? false) && mounted) {
      await _showWebViewLogin(navigator);
    }
  }

  /// Attempts to login with captcha code.
  Future<void> _loginWithCaptcha(
    String captchaCode,
    RutrackerCaptchaData captchaData,
  ) async {
    if (!mounted) return;

    setState(() {
      _statusMessage =
          AppLocalizations.of(context)?.loggingInText ?? 'Logging in...';
      _statusColor = Colors.blue;
      _isLoggingIn = true;
    });

    try {
      final repository = ref.read(authRepositoryProvider);
      final success = await repository.loginWithCaptcha(
        _usernameController.text,
        _passwordController.text,
        captchaCode,
        captchaData,
      );

      if (success && _rememberMe) {
        await repository.saveCredentials(
          username: _usernameController.text,
          password: _passwordController.text,
          rememberMe: _rememberMe,
        );
      }

      if (mounted) {
        setState(() {
          _statusMessage = success
              ? (AppLocalizations.of(context)?.loginSuccessMessage ??
                  'Login successful!')
              : (AppLocalizations.of(context)?.loginFailedMessage ??
                  'Login failed. Please check credentials');
          _statusColor = success ? Colors.green : Colors.red;
          _isLoggingIn = false;
        });

        if (success) {
          await _testConnection();

          // CRITICAL: Refresh auth status and invalidate providers to update cache
          // This ensures all providers (authStatusProvider, isLoggedInProvider) are updated
          final repository = ref.read(authRepositoryProvider);
          await repository.refreshAuthStatus();
          ref.invalidate(isLoggedInProvider);
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _statusMessage = AppLocalizations.of(context)
                  ?.loginFailedWithError(e.toString()) ??
              'Login failed: ${e.toString()}';
          _statusColor = Colors.red;
          _isLoggingIn = false;
        });
      }
    }
  }

  /// Shows WebView login and handles the result.
  Future<void> _showWebViewLogin(NavigatorState navigator) async {
    final result = await navigator.push(
      MaterialPageRoute(
        builder: (context) => const SecureRutrackerWebView(),
      ),
    );

    // If login was successful via WebView, wait for cookie sync and test connection
    // Check that result is not null and not empty (empty string means no cookies)
    if (result != null && result.isNotEmpty && mounted) {
      setState(() {
        _statusMessage = AppLocalizations.of(context)?.testingConnectionText ??
            'Syncing cookies...';
        _statusColor = Colors.blue;
      });

      // CRITICAL: Wait for cookie sync to complete with retries
      // Cookies need time to sync from WebView to CookieManager to Dio
      var cookiesSynced = false;
      for (var attempt = 0; attempt < 5; attempt++) {
        await Future.delayed(Duration(milliseconds: 300 + (attempt * 200)));

        final hasCookies = await DioClient.hasValidCookies();
        if (hasCookies) {
          cookiesSynced = true;
          break;
        }
      }

      if (!cookiesSynced && mounted) {
        setState(() {
          _statusMessage = AppLocalizations.of(context)
                  ?.authenticationFailedMessage ??
              'Cookies synchronization failed. Please try logging in again.';
          _statusColor = Colors.red;
        });
        return;
      }

      // Test connection after cookie sync
      await _testConnection();

      // CRITICAL: Refresh auth status and invalidate providers to update cache
      // This ensures all providers are updated after successful WebView login
      final repository = ref.read(authRepositoryProvider);
      await repository.refreshAuthStatus();
      ref.invalidate(isLoggedInProvider);
    }
  }

  Future<void> _testConnection() async {
    setState(() {
      _statusMessage = AppLocalizations.of(context)?.testingConnectionText ??
          'Testing connection...';
      _statusColor = Colors.blue;
    });

    try {
      final isValid = await DioClient.validateCookies();
      if (mounted) {
        setState(() {
          _statusMessage = isValid
              ? (AppLocalizations.of(context)?.loginSuccessMessage ??
                  'Connection successful!')
              : (AppLocalizations.of(context)?.loginFailedMessage ??
                  'Connection failed. Please check your credentials.');
          _statusColor = isValid ? Colors.green : Colors.red;
        });
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _statusMessage = AppLocalizations.of(context)
                  ?.loginFailedWithError(e.toString()) ??
              'Connection failed: ${e.toString()}';
          _statusColor = Colors.red;
        });
      }
    }
  }

  Future<void> _logout() async {
    setState(() {
      _statusMessage =
          AppLocalizations.of(context)?.loggingOutText ?? 'Logging out...';
      _statusColor = Colors.blue;
    });

    try {
      final repository = ref.read(authRepositoryProvider);
      await repository.logout();
      await repository.clearStoredCredentials();

      setState(() {
        _usernameController.clear();
        _passwordController.clear();
        _statusMessage = AppLocalizations.of(context)?.logoutSuccessMessage ??
            'Logged out successfully';
        _statusColor = Colors.green;
      });
    } on Exception catch (e) {
      setState(() {
        _statusMessage =
            AppLocalizations.of(context)!.logoutErrorMessage(e.toString());
        _statusColor = Colors.red;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // Log that build is being called
    safeUnawaited(
      StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'AuthScreen build called',
        context: 'auth_screen_build',
      ),
    );

    // CRITICAL: Don't read providers in build() - they can hang and cause white screen!
    // Use only local state (_statusMessage, _statusColor) for UI rendering
    // This matches old version (1.2.5) behavior where providers were NOT read in build()
    // Status will be updated asynchronously via _refreshAuthStatusAsync() and setState()
    final isLoggedIn = _statusColor == Colors.green &&
        _statusMessage.isNotEmpty &&
        !_statusMessage.toLowerCase().contains('failed') &&
        !_statusMessage.toLowerCase().contains('error');

    return Scaffold(
      appBar: AppBar(
        title: Text((AppLocalizations.of(context)?.authScreenTitle ??
                'RuTracker Connection')
            .withFlavorSuffix()),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Status indicator
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          Icon(
                            isLoggedIn ? Icons.check_circle : Icons.error,
                            color: _statusColor,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _statusMessage.isEmpty
                                  ? (AppLocalizations.of(context)
                                          ?.authScreenTitle ??
                                      'RuTracker Connection')
                                  : _statusMessage,
                              style: TextStyle(color: _statusColor),
                            ),
                          ),
                        ],
                      ),
                      if (_isLoggingIn) const LinearProgressIndicator(),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 20),

              // Login form (only show when not logged in)
              // Use local state to determine if logged in
              if (!isLoggedIn) ...[
                AutofillGroup(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        controller: _usernameController,
                        autofillHints: const [
                          AutofillHints.username,
                          AutofillHints.email,
                        ],
                        textInputAction: TextInputAction.next,
                        keyboardType: TextInputType.text,
                        enableSuggestions: false,
                        autocorrect: false,
                        decoration: InputDecoration(
                          labelText:
                              AppLocalizations.of(context)?.usernameLabelText ??
                                  'Username',
                          border: const OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: _passwordController,
                        autofillHints: const [AutofillHints.password],
                        textInputAction: TextInputAction.done,
                        obscureText: true,
                        enableSuggestions: false,
                        autocorrect: false,
                        onSubmitted: (_) => _login(),
                        decoration: InputDecoration(
                          labelText:
                              AppLocalizations.of(context)?.passwordLabelText ??
                                  'Password',
                          border: const OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          Checkbox(
                            value: _rememberMe,
                            onChanged: (value) {
                              setState(() {
                                _rememberMe = value ?? true;
                              });
                            },
                          ),
                          Expanded(
                            child: Text(
                              AppLocalizations.of(context)?.rememberMeLabel ??
                                  'Remember me',
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton(
                        onPressed: _isLoggingIn ? null : _login,
                        child: Text(
                            AppLocalizations.of(context)?.loginButtonText ??
                                'Login'),
                      ),
                    ],
                  ),
                ),
              ],

              // Logout and test buttons (when logged in)
              if (isLoggedIn) ...[
                ElevatedButton(
                  onPressed: _testConnection,
                  child: Text(
                      AppLocalizations.of(context)?.testConnectionButtonText ??
                          'Test Connection'),
                ),
                const SizedBox(height: 10),
                ElevatedButton(
                  onPressed: _logout,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                  ),
                  child: Text(AppLocalizations.of(context)?.logoutButtonText ??
                      'Logout'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }
}
