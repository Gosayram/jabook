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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/features/auth/presentation/screens/auth_screen.dart';
import 'package:jabook/features/debug/presentation/screens/debug_screen.dart';
import 'package:jabook/features/downloads/presentation/screens/downloads_screen.dart';
import 'package:jabook/features/library/presentation/screens/favorites_screen.dart';
import 'package:jabook/features/library/presentation/screens/library_screen.dart';
import 'package:jabook/features/library/presentation/screens/storage_management_screen.dart';
import 'package:jabook/features/library/presentation/screens/trash_screen.dart';
import 'package:jabook/features/mirrors/presentation/screens/mirrors_screen.dart';
import 'package:jabook/features/player/presentation/screens/local_player_screen.dart';
import 'package:jabook/features/player/presentation/screens/player_screen.dart';
import 'package:jabook/features/player/presentation/widgets/mini_player_widget.dart';
import 'package:jabook/features/search/presentation/screens/search_screen.dart';
import 'package:jabook/features/settings/presentation/screens/settings_screen.dart';
import 'package:jabook/features/topic/presentation/screens/topic_screen.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Provides the main application router configuration.
///
/// This provider sets up the navigation structure for the app using GoRouter,
/// defining all available routes and their corresponding builders.
final appRouterProvider = Provider<GoRouter>((ref) {
  final config = AppConfig();
  final routes = <RouteBase>[
    ShellRoute(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => const LibraryScreen(),
        ),
        GoRoute(
          path: '/search',
          builder: (context, state) {
            EnvironmentLogger().d('GoRouter building SearchScreen at /search');
            return const SearchScreen();
          },
        ),
        GoRoute(
          path: '/downloads',
          builder: (context, state) {
            // Get downloadId from query parameters if present
            final downloadId = state.uri.queryParameters['downloadId'];
            return DownloadsScreen(highlightDownloadId: downloadId);
          },
        ),
        GoRoute(
          path: '/settings',
          builder: (context, state) => const SettingsScreen(),
        ),
        // Only include debug route if debug features are enabled
        if (config.debugFeaturesEnabled)
          GoRoute(
            path: '/debug',
            builder: (context, state) => const DebugScreen(),
          ),
      ],
      builder: (context, state, child) => _MainNavigationWrapper(
        debugEnabled: config.debugFeaturesEnabled,
        child: child,
      ),
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
      path: '/local-player',
      builder: (context, state) {
        final group = state.extra as LocalAudiobookGroup?;
        if (group == null) {
          // Fallback if group is not provided
          return Scaffold(
            appBar: AppBar(title: const Text('Error')),
            body: const Center(child: Text('No audiobook group provided')),
          );
        }
        return LocalPlayerScreen(group: group);
      },
    ),
    GoRoute(
      path: '/mirrors',
      builder: (context, state) => const MirrorsScreen(),
    ),
    GoRoute(
      path: '/favorites',
      builder: (context, state) => const FavoritesScreen(),
    ),
    GoRoute(
      path: '/auth',
      builder: (context, state) => const AuthScreen(),
    ),
    GoRoute(
      path: '/storage-management',
      builder: (context, state) => const StorageManagementScreen(),
    ),
    GoRoute(
      path: '/trash',
      builder: (context, state) => const TrashScreen(),
    ),
  ];

  return GoRouter(
    routes: routes,
    // Use redirect to control navigation and prevent building screens before ready
    redirect: (context, state) => null, // null means proceed with navigation
    initialLocation: '/',
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)?.error ?? 'Error'),
      ),
      body: Center(
        child: Text(state.error.toString()),
      ),
    ),
  );
});

/// Widget that provides bottom navigation for the main app screens.
class _MainNavigationWrapper extends ConsumerStatefulWidget {
  const _MainNavigationWrapper({
    required this.child,
    required this.debugEnabled,
  });

  final Widget child;
  final bool debugEnabled;

  @override
  ConsumerState<_MainNavigationWrapper> createState() =>
      _MainNavigationWrapperState();
}

class _MainNavigationWrapperState
    extends ConsumerState<_MainNavigationWrapper> {
  int _selectedIndex = 0;

  List<NavigationItem> _buildNavigationItems(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    final items = [
      NavigationItem(
        title: localizations?.navLibrary ??
            localizations?.libraryTitle ??
            'Library',
        icon: Icons.library_books,
        route: '/',
      ),
      NavigationItem(
        title: localizations?.navSearch ?? 'Search',
        icon: Icons.search,
        route: '/search',
      ),
      NavigationItem(
        title: localizations?.downloadsTitle ?? 'Downloads',
        icon: Icons.download,
        route: '/downloads',
      ),
      NavigationItem(
        title: localizations?.navSettings ??
            localizations?.settingsTitle ??
            'Settings',
        icon: Icons.settings,
        route: '/settings',
      ),
    ];

    // Only add debug tab if debug features are enabled
    if (widget.debugEnabled) {
      items.add(
        NavigationItem(
          title:
              localizations?.navDebug ?? localizations?.debugTitle ?? 'Debug',
          icon: Icons.bug_report,
          route: '/debug',
        ),
      );
    }

    return items;
  }

  @override
  Widget build(BuildContext context) {
    // Get current location to determine selected index
    // Safely get router state - handle case where router is not ready yet
    var currentLocation = '/';
    try {
      final routerState = GoRouterState.of(context);
      currentLocation = routerState.uri.toString();
    } on Exception {
      // Router not ready yet - use default location
      currentLocation = '/';
    }
    final navigationItems = _buildNavigationItems(context);
    _selectedIndex =
        navigationItems.indexWhere((item) => item.route == currentLocation);
    if (_selectedIndex == -1) _selectedIndex = 0;

    return Scaffold(
      body: widget.child,
      persistentFooterButtons: const [
        MiniPlayerWidget(),
      ],
      bottomNavigationBar: Semantics(
        explicitChildNodes: true,
        child: BottomNavigationBar(
          currentIndex: _selectedIndex,
          onTap: (index) {
            final route = navigationItems[index].route;
            EnvironmentLogger().d(
              'BottomNavigationBar onTap: index=$index, route=$route, currentLocation=$currentLocation',
            );
            if (route != currentLocation) {
              EnvironmentLogger().d('Navigating to route: $route');
              context.go(route);
            } else {
              EnvironmentLogger()
                  .d('Already on route $route, skipping navigation');
            }
          },
          type: BottomNavigationBarType.fixed,
          items: navigationItems
              .map((item) => BottomNavigationBarItem(
                    icon: Stack(
                      children: [
                        Semantics(
                          label: item.title,
                          child: Icon(item.icon),
                        ),
                        if (item.badge != null)
                          Positioned(
                            top: 0,
                            right: 0,
                            child: item.badge!,
                          ),
                      ],
                    ),
                    label: item.title,
                  ))
              .toList(),
        ),
      ),
    );
  }
}

/// Data class for navigation items.
class NavigationItem {
  /// Creates a new navigation item.
  const NavigationItem({
    required this.title,
    required this.icon,
    required this.route,
    this.badge,
  });

  /// The title of the navigation item.
  final String title;

  /// The icon to display for this navigation item.
  final IconData icon;

  /// The route path associated with this navigation item.
  final String route;

  /// Optional badge widget to display on the navigation item.
  final Widget? badge;
}
