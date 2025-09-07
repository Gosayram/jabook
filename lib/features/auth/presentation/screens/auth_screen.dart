import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
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
        _statusMessage = 'Please enter username and password';
        _statusColor = Colors.red;
      });
      return;
    }

    setState(() {
      _statusMessage = 'Logging in...';
      _statusColor = Colors.blue;
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

      setState(() {
        _statusMessage = success
            ? 'Login successful!'
            : 'Login failed. Please check credentials';
        _statusColor = success ? Colors.green : Colors.red;
      });

      if (success) {
        // Test connection after successful login
        await _testConnection();
      }
    } on Exception catch (e) {
      setState(() {
        _statusMessage = 'Login error: ${e.toString()}';
        _statusColor = Colors.red;
      });
    }
  }

  Future<void> _testConnection() async {
    setState(() {
      _statusMessage = 'Testing connection...';
      _statusColor = Colors.blue;
    });

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      
      setState(() {
        _statusMessage = 'Connection successful! Using: $activeEndpoint';
        _statusColor = Colors.green;
      });
    } on Exception catch (e) {
      setState(() {
        _statusMessage = 'Connection test failed: ${e.toString()}';
        _statusColor = Colors.red;
      });
    }
  }

  Future<void> _logout() async {
    setState(() {
      _statusMessage = 'Logging out...';
      _statusColor = Colors.blue;
    });

    try {
      final repository = ref.read(authRepositoryProvider);
      await repository.logout();
      await repository.clearStoredCredentials();

      setState(() {
        _usernameController.clear();
        _passwordController.clear();
        _statusMessage = 'Logged out successfully';
        _statusColor = Colors.green;
      });
    } on Exception catch (e) {
      setState(() {
        _statusMessage = 'Logout error: ${e.toString()}';
        _statusColor = Colors.red;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final authStatus = ref.watch(authStatusProvider);
    final isLoggedIn = ref.watch(isLoggedInProvider);
    
    return Scaffold(
      appBar: AppBar(
        title: Text(localizations?.authTitle ?? 'RuTracker Connection'),
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
                          isLoggedIn.valueOrNull ?? false ? Icons.check_circle : Icons.error,
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
            if (!(isLoggedIn.valueOrNull ?? false)) ...[
              TextField(
                controller: _usernameController,
                decoration: InputDecoration(
                  labelText: localizations?.usernameLabel ?? 'Username',
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _passwordController,
                decoration: InputDecoration(
                  labelText: localizations?.passwordLabel ?? 'Password',
                  border: const OutlineInputBorder(),
                ),
                obscureText: true,
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Checkbox(
                    value: _rememberMe,
                    onChanged: (value) => setState(() => _rememberMe = value ?? true),
                  ),
                  Text(localizations?.rememberMeLabel ?? 'Remember me'),
                ],
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _login,
                child: Text(localizations?.loginButton ?? 'Login'),
              ),
            ],

            // Logout and test buttons (when logged in)
            if (isLoggedIn.valueOrNull ?? false) ...[
              ElevatedButton(
                onPressed: _testConnection,
                child: Text(localizations?.testConnectionButton ?? 'Test Connection'),
              ),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: _logout,
                child: Text(localizations?.logoutButton ?? 'Logout'),
              ),
            ],

            const Spacer(),

            // Help text
            Text(
              localizations?.authHelpText ?? 
              'Login to RuTracker to access audiobook search and downloads. '
              'Your credentials are stored securely.',
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