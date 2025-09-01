import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/app/router/app_router.dart';
import 'package:jabook/app/theme/app_theme.dart';

/// Main application widget for JaBook audiobook player.
///
/// This widget serves as the root of the application and sets up
/// the Material Design app with routing and theming.
class JaBookApp extends ConsumerWidget {
  /// Creates a new instance of JaBookApp.
  ///
  /// The [key] parameter is used to control how one widget replaces another
  /// in the tree.
  const JaBookApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) => MaterialApp.router(
    title: 'JaBook',
    theme: AppTheme.lightTheme,
    darkTheme: AppTheme.darkTheme,
    routerConfig: ref.watch(appRouterProvider),
  );
}