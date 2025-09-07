import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/features/auth/data/providers/auth_provider.dart';

/// Widget that displays authentication status in the navigation bar.
class AuthStatusIndicator extends ConsumerWidget {
  /// Creates a new AuthStatusIndicator instance.
  const AuthStatusIndicator({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isLoggedIn = ref.watch(isLoggedInProvider);

    final isAuthenticated = isLoggedIn.valueOrNull ?? false;
    
    return Icon(
      isAuthenticated ? Icons.check_circle : Icons.error_outline,
      size: 16,
      color: isAuthenticated ? Colors.green : Colors.orange,
    );
  }
}