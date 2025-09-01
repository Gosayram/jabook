import 'package:flutter/material.dart';

/// Main audiobook player screen.
///
/// This screen provides the user interface for playing audiobooks,
/// including playback controls, progress tracking, and chapter navigation.
class PlayerScreen extends StatelessWidget {

  /// Creates a new PlayerScreen instance.
  ///
  /// The [bookId] parameter is required to identify which audiobook
  /// should be displayed and played.
  const PlayerScreen({super.key, required this.bookId});
  
  /// The unique identifier of the audiobook to play.
  final String bookId;

  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(
        title: Text('Player: $bookId'),
      ),
      body: const Center(
        child: Text('Player Screen'),
      ),
    );
}