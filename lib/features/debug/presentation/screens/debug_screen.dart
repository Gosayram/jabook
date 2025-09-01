import 'package:flutter/material.dart';

/// Debug screen for development and troubleshooting purposes.
///
/// This screen provides debugging tools and information to help
/// developers diagnose issues during development and testing.
class DebugScreen extends StatelessWidget {
  /// Creates a new DebugScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const DebugScreen({super.key});

  @override
  Widget build(BuildContext context) => const Scaffold(
      body: Center(
        child: Text('Debug Screen'),
      ),
    );
}