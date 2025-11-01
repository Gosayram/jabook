import 'package:flutter/material.dart';
import 'package:jabook/core/net/dio_client.dart';

/// Widget that displays authentication status based on cookie validity.
class AuthStatusIndicator extends StatefulWidget {
  /// Creates a new AuthStatusIndicator instance.
  const AuthStatusIndicator({super.key});

  @override
  State<AuthStatusIndicator> createState() => _AuthStatusIndicatorState();
}

class _AuthStatusIndicatorState extends State<AuthStatusIndicator> {
  bool? _isAuthenticated;
  bool _isChecking = false;

  @override
  void initState() {
    super.initState();
    _checkAuthStatus();
  }

  Future<void> _checkAuthStatus() async {
    setState(() {
      _isChecking = true;
    });

    try {
      final hasCookies = await DioClient.hasValidCookies();
      setState(() {
        _isAuthenticated = hasCookies;
        _isChecking = false;
      });
    } on Exception {
      setState(() {
        _isAuthenticated = false;
        _isChecking = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isChecking) {
      return const SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(strokeWidth: 2),
      );
    }

    return Tooltip(
      message: _isAuthenticated == true ? 'Authenticated' : 'Not authenticated',
      child: Icon(
        _isAuthenticated == true ? Icons.check_circle : Icons.error_outline,
        size: 16,
        color: _isAuthenticated == true ? Colors.green : Colors.orange,
      ),
    );
  }
}
