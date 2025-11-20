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
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/session/auth_error_handler.dart';
import 'package:jabook/features/auth/data/providers/auth_provider.dart';
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
                  'Login failed. Please check credentials');
          _statusColor = success ? Colors.green : Colors.red;
          _isLoggingIn = false;
        });

        if (success) {
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
            _statusMessage = 'Captcha verification required. Please try again later.';
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
            _statusMessage = 'Network error. Please check your connection and try again.';
          } else {
            final errorString = e.toString();
            _statusMessage = AppLocalizations.of(context)?.loginFailedMessage ??
                'Login failed: $errorString';
          }
          _statusColor = Colors.red;
          _isLoggingIn = false;
        });
      }
    }
  }

  Future<void> _testConnection() async {
    setState(() {
      _statusMessage = AppLocalizations.of(context)?.testingConnectionText ??
          'Testing connection...';
      _statusColor = Colors.blue;
    });

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();

      setState(() {
        _statusMessage = AppLocalizations.of(context)!
            .connectionSuccessMessage(activeEndpoint);
        _statusColor = Colors.green;
      });
    } on Exception catch (e) {
      setState(() {
        _statusMessage = AppLocalizations.of(context)!
            .connectionFailedMessage(e.toString());
        _statusColor = Colors.red;
      });
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
        _statusMessage = AppLocalizations.of(context)!
            .logoutErrorMessage(e.toString());
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
        title: Text(AppLocalizations.of(context)?.authScreenTitle ??
            'RuTracker Connection'),
      ),
      body: Padding(
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
              TextField(
                controller: _usernameController,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context)?.usernameLabelText ??
                      'Username',
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _passwordController,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context)?.passwordLabelText ??
                      'Password',
                  border: const OutlineInputBorder(),
                ),
                obscureText: true,
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
                    : Text(
                        AppLocalizations.of(context)?.loginButtonText ?? 'Login'),
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
                child: Text(
                    AppLocalizations.of(context)?.logoutButtonText ?? 'Logout'),
              ),
            ],

            const Spacer(),

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
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }
}
