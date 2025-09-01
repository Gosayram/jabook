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
      title: const Text('Error'),
    ),
    body: Center(
      child: Text(state.error.toString()),
    ),
  ),
));

/// Widget that provides bottom navigation for the main app screens.
class _MainNavigationWrapper extends ConsumerStatefulWidget {
  final Widget child;

  const _MainNavigationWrapper({required this.child});

  @override
  ConsumerState<_MainNavigationWrapper> createState() => _MainNavigationWrapperState();
}

class _MainNavigationWrapperState extends ConsumerState<_MainNavigationWrapper> {
  int _selectedIndex = 0;

  final List<NavigationItem> _navigationItems = [
    const NavigationItem(
      title: 'Library',
      icon: Icons.library_books,
      route: '/',
    ),
    const NavigationItem(
      title: 'Search',
      icon: Icons.search,
      route: '/search',
    ),
    const NavigationItem(
      title: 'Settings',
      icon: Icons.settings,
      route: '/settings',
    ),
    const NavigationItem(
      title: 'Debug',
      icon: Icons.bug_report,
      route: '/debug',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    // Get current location to determine selected index
    final currentLocation = GoRouterState.of(context).uri.toString();
    _selectedIndex = _navigationItems.indexWhere((item) => item.route == currentLocation);
    if (_selectedIndex == -1) _selectedIndex = 0;

    return Scaffold(
      body: widget.child,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: (index) {
          final route = _navigationItems[index].route;
          if (route != currentLocation) {
            context.go(route);
          }
        },
        type: BottomNavigationBarType.fixed,
        items: _navigationItems.map((item) => BottomNavigationBarItem(
          icon: Icon(item.icon),
          label: item.title,
        )).toList(),
      ),
    );
  }
}

/// Data class for navigation items.
class NavigationItem {
  final String title;
  final IconData icon;
  final String route;

  const NavigationItem({
    required this.title,
    required this.icon,
    required this.route,
  });
}