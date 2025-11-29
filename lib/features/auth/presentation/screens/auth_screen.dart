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
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/session/auth_error_handler.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
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
    // Initial status check
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(authRepositoryProvider).refreshAuthStatus();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Check auth status when screen is shown
    ref.read(authRepositoryProvider).refreshAuthStatus();
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
                  AppLocalizations.of(context)?.loginFailedMessage ??
                  'Login failed. Please check credentials');
          _statusColor = success ? Colors.green : Colors.red;
          _isLoggingIn = false;
        });

        if (success) {
          // Autofill service will automatically save credentials if configured
          // The autofillHints in TextFields enable this functionality

          // Test connection after successful login
          await _testConnection();

          // Return true to indicate successful login
          if (mounted) {
            Navigator.of(context).pop(true);
          }
        }
      }
    } on AuthFailure catch (e) {
      // Use AuthErrorHandler for authentication errors
      if (mounted) {
        AuthErrorHandler.showAuthErrorSnackBar(context, e);
        setState(() {
          // Provide user-friendly error messages
          if (e.message.contains('Invalid username or password') ||
              e.message.contains('wrong username/password')) {
            _statusMessage = AppLocalizations.of(context)?.loginFailedMessage ??
                'Invalid username or password. Please check your credentials.';
          } else if (e.message.contains('captcha')) {
            _statusMessage =
                AppLocalizations.of(context)?.captchaVerificationRequired ??
                    'Captcha verification required. Please try again later.';
            // Show captcha dialog with data from AuthFailure
            _showCaptchaDialog(
              context,
              captchaType: e.captchaType as CaptchaType?,
              rutrackerCaptchaData:
                  e.rutrackerCaptchaData as RutrackerCaptchaData?,
            );
          } else {
            _statusMessage = e.message;
          }
          _statusColor = Colors.red;
          _isLoggingIn = false;
        });
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          // Provide user-friendly error messages
          final errorMsg = e.toString().toLowerCase();
          if (errorMsg.contains('timeout') || errorMsg.contains('connection')) {
            _statusMessage = AppLocalizations.of(context)
                    ?.networkErrorCheckConnection ??
                'Network error. Please check your connection and try again.';
          } else if (errorMsg.contains('authentication required') ||
              errorMsg.contains('network null') ||
              errorMsg.contains('null')) {
            // Handle authentication errors that might show as "network null"
            _statusMessage = AppLocalizations.of(context)
                    ?.authenticationFailedMessage ??
                'Authentication failed. Please check your credentials and try again.';
          } else {
            final errorString = e.toString();
            _statusMessage = AppLocalizations.of(context)
                    ?.loginFailedWithError(errorString) ??
                'Login failed: $errorString';
          }
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
          if (mounted) {
            Navigator.of(context).pop(true);
          }
        }
      }
    } on AuthFailure catch (e) {
      if (mounted) {
        AuthErrorHandler.showAuthErrorSnackBar(context, e);
        setState(() {
          if (e.message.contains('captcha')) {
            _statusMessage =
                AppLocalizations.of(context)?.captchaVerificationRequired ??
                    'Captcha verification required. Please try again.';
            // Show captcha dialog again with new data (if available)
            _showCaptchaDialog(
              context,
              captchaType: e.captchaType as CaptchaType?,
              rutrackerCaptchaData:
                  e.rutrackerCaptchaData as RutrackerCaptchaData?,
            );
          } else {
            _statusMessage = e.message;
          }
          _statusColor = Colors.red;
          _isLoggingIn = false;
        });
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

      // Test connection with real authentication check
      final connectionSuccess = await _testConnection();
      if (connectionSuccess && mounted) {
        // CRITICAL: Refresh auth status to notify the app that user is authenticated
        final repository = ref.read(authRepositoryProvider);
        await repository.refreshAuthStatus();

        navigator.pop(true);
      } else if (mounted) {
        // Connection test failed - cookies may not have synced properly
        setState(() {
          _statusMessage =
              AppLocalizations.of(context)?.authenticationFailedMessage ??
                  'Authentication validation failed. Please try again.';
          _statusColor = Colors.red;
        });
      }
    }
  }

  /// Tests connection and verifies authentication is working.
  ///
  /// Returns true if authentication is valid, false otherwise.
  Future<bool> _testConnection() async {
    setState(() {
      _statusMessage = AppLocalizations.of(context)?.testingConnectionText ??
          'Testing connection...';
      _statusColor = Colors.blue;
    });

    try {
      // CRITICAL: Actually verify authentication by checking cookies
      final hasValidCookies = await DioClient.hasValidCookies();

      if (!hasValidCookies) {
        setState(() {
          _statusMessage =
              AppLocalizations.of(context)?.authenticationFailedMessage ??
                  'Authentication failed. No valid cookies found.';
          _statusColor = Colors.red;
        });
        return false;
      }

      // Also validate cookies by making a test request
      final cookiesValid = await DioClient.validateCookies();

      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();

      if (cookiesValid) {
        setState(() {
          _statusMessage = AppLocalizations.of(context)!
              .connectionSuccessMessage(activeEndpoint);
          _statusColor = Colors.green;
        });
        return true;
      } else {
        setState(() {
          _statusMessage =
              AppLocalizations.of(context)?.authenticationFailedMessage ??
                  'Authentication validation failed. Cookies may be expired.';
          _statusColor = Colors.red;
        });
        return false;
      }
    } on Exception catch (e) {
      setState(() {
        _statusMessage =
            AppLocalizations.of(context)!.connectionFailedMessage(e.toString());
        _statusColor = Colors.red;
      });
      return false;
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
    final authStatus = ref.watch(authStatusProvider);
    final isLoggedIn = ref.watch(isLoggedInProvider);

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
                            isLoggedIn.value ?? false
                                ? Icons.check_circle
                                : Icons.error,
                            color: _statusColor,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _statusMessage,
                              style: TextStyle(color: _statusColor),
                            ),
                          ),
                        ],
                      ),
                      if (authStatus.isLoading || isLoggedIn.isLoading)
                        const LinearProgressIndicator(),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 20),

              // Login form (only show when not logged in)
              if (!(isLoggedIn.value ?? false)) ...[
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
                          hintText: 'Enter your RuTracker username',
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
                          hintText: 'Enter your password',
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Checkbox(
                      value: _rememberMe,
                      onChanged: (value) => setState(() {
                        _rememberMe = value ?? true;
                      }),
                    ),
                    Text(AppLocalizations.of(context)?.rememberMeLabelText ??
                        'Remember me'),
                  ],
                ),
                const SizedBox(height: 20),
                ElevatedButton(
                  onPressed: _isLoggingIn ? null : _login,
                  child: _isLoggingIn
                      ? Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                              ),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              AppLocalizations.of(context)?.loggingInText ??
                                  'Logging in...',
                            ),
                          ],
                        )
                      : Text(AppLocalizations.of(context)?.loginButtonText ??
                          'Login'),
                ),
              ],

              // Logout and test buttons (when logged in)
              if (isLoggedIn.value ?? false) ...[
                ElevatedButton(
                  onPressed: _testConnection,
                  child: Text(
                      AppLocalizations.of(context)?.testConnectionButtonText ??
                          'Test Connection'),
                ),
                const SizedBox(height: 16),
                OutlinedButton(
                  onPressed: _logout,
                  child: Text(AppLocalizations.of(context)?.logoutButtonText ??
                      'Logout'),
                ),
              ],

              const SizedBox(height: 20),

              // Help text
              Text(
                AppLocalizations.of(context)?.authHelpText ??
                    'Login to RuTracker to access audiobook search and downloads. Your credentials are stored securely.',
                style: Theme.of(context).textTheme.bodySmall,
                textAlign: TextAlign.center,
              ),
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
