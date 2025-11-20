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
      message:
          (_isAuthenticated ?? false) ? 'Authenticated' : 'Not authenticated',
      child: Icon(
        (_isAuthenticated ?? false) ? Icons.check_circle : Icons.error_outline,
        size: 16,
        color: (_isAuthenticated ?? false) ? Colors.green : Colors.orange,
      ),
    );
  }
}
