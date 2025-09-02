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
import 'package:jabook/l10n/app_localizations.dart';

/// Provides the main application router configuration.
///
/// This provider sets up the navigation structure for the app using GoRouter,
/// defining all available routes and their corresponding builders.
final appRouterProvider = Provider<GoRouter>((ref) => GoRouter(
  routes: [
    ShellRoute(
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
          path: '/settings',
          builder: (context, state) => const SettingsScreen(),
        ),
        GoRoute(
          path: '/debug',
          builder: (context, state) => const DebugScreen(),
        ),
      ],
      builder: (context, state, child) => _MainNavigationWrapper(child: child),
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
  ],
  initialLocation: '/',
  errorBuilder: (context, state) => Scaffold(
    appBar: AppBar(
      title: Text(AppLocalizations.of(context)?.error ?? 'Error'),
    ),
    body: Center(
      child: Text(state.error.toString()),
    ),
  ),
));

/// Widget that provides bottom navigation for the main app screens.
class _MainNavigationWrapper extends ConsumerStatefulWidget {
  const _MainNavigationWrapper({required this.child});

  final Widget child;

  @override
  ConsumerState<_MainNavigationWrapper> createState() => _MainNavigationWrapperState();
}

class _MainNavigationWrapperState extends ConsumerState<_MainNavigationWrapper> {
  int _selectedIndex = 0;

  List<NavigationItem> _buildNavigationItems(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    
    return [
      NavigationItem(
        title: localizations?.libraryTitle ?? 'Library',
        icon: Icons.library_books,
        route: '/',
      ),
      NavigationItem(
        title: localizations?.searchAudiobooks ?? 'Search',
        icon: Icons.search,
        route: '/search',
      ),
      NavigationItem(
        title: localizations?.settingsTitle ?? 'Settings',
        icon: Icons.settings,
        route: '/settings',
      ),
      NavigationItem(
        title: localizations?.debugTitle ?? 'Debug',
        icon: Icons.bug_report,
        route: '/debug',
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    // Get current location to determine selected index
    final currentLocation = GoRouterState.of(context).uri.toString();
    final navigationItems = _buildNavigationItems(context);
    _selectedIndex = navigationItems.indexWhere((item) => item.route == currentLocation);
    if (_selectedIndex == -1) _selectedIndex = 0;

    return Scaffold(
      body: widget.child,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: (index) {
          final route = navigationItems[index].route;
          if (route != currentLocation) {
            context.go(route);
          }
        },
        type: BottomNavigationBarType.fixed,
        items: navigationItems.map((item) => BottomNavigationBarItem(
          icon: Icon(item.icon),
          label: item.title,
        )).toList(),
      ),
    );
  }
}

/// Data class for navigation items.
class NavigationItem {
  /// Creates a new navigation item.
  ///
  /// All parameters are required.
  const NavigationItem({
    required this.title,
    required this.icon,
    required this.route,
  });

  /// The title of the navigation item.
  final String title;

  /// The icon to display for this navigation item.
  final IconData icon;

  /// The route path associated with this navigation item.
  final String route;
}
