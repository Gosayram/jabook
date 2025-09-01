
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Main screen for displaying the user's audiobook library.
///
/// This screen shows the user's collection of downloaded and favorited
/// audiobooks, with options to search, filter, and add new books.
class LibraryScreen extends ConsumerWidget {
  /// Creates a new LibraryScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) => Scaffold(
      appBar: AppBar(
        title: const Text('My Library'),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () {
              // Navigate to search screen
              context.go('/search');
            },
          ),
          IconButton(
            icon: const Icon(Icons.filter_list),
            onPressed: () {
              // Show filter options - navigate to settings for now
              context.go('/settings');
            },
          ),
        ],
      ),
      body: const _LibraryContent(),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          // TODO: Implement add book functionality
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Add book functionality coming soon!')),
          );
        },
        child: const Icon(Icons.add),
      ),
    );
}

/// Private widget for displaying the main library content.
///
/// This widget contains the actual content of the library screen,
/// including the list of audiobooks and any filtering/sorting options.
class _LibraryContent extends ConsumerWidget {
  /// Creates a new _LibraryContent instance.
  const _LibraryContent();

  @override
  Widget build(BuildContext context, WidgetRef ref) => const Center(
      child: Text('Library content will be displayed here'),
    );
}