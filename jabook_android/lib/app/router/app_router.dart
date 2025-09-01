import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:jabook/features/debug/presentation/screens/debug_screen.dart';
import 'package:jabook/features/library/presentation/screens/library_screen.dart';
import 'package:jabook/features/mirrors/presentation/screens/mirrors_screen.dart';
import 'package:jabook/features/player/presentation/screens/player_screen.dart';
import 'package:jabook/features/search/presentation/screens/search_screen.dart';
import 'package:jabook/features/settings/presentation/screens/settings_screen.dart';
import 'package:jabook/features/topic/presentation/screens/topic_screen.dart';

/// Provides the main application router configuration.
///
/// This provider sets up the navigation structure for the app using GoRouter,
/// defining all available routes and their corresponding builders.
final appRouterProvider = Provider<GoRouter>((ref) => GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const LibraryScreen(),
    ),
    GoRoute(
      path: '/search',
      builder: (context, state) => const SearchScreen(),
    ),
    GoRoute(
      path: '/topic/:topicId',
      builder: (context, state) => TopicScreen(
        topicId: state.pathParameters['topicId'] ?? '',
      ),
    ),
    GoRoute(
      path: '/player/:bookId',
      builder: (context, state) => PlayerScreen(
        bookId: state.pathParameters['bookId'] ?? '',
      ),
    ),
    GoRoute(
      path: '/mirrors',
      builder: (context, state) => const MirrorsScreen(),
    ),
    GoRoute(
      path: '/debug',
      builder: (context, state) => const DebugScreen(),
    ),
    GoRoute(
      path: '/settings',
      builder: (context, state) => const SettingsScreen(),
    ),
  ],
  initialLocation: '/',
  errorBuilder: (context, state) => Scaffold(
    appBar: AppBar(
      title: const Text('Error'),
    ),
    body: Center(
      child: Text(state.error.toString()),
    ),
  ),
));