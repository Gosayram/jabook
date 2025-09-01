import 'package:flutter/material.dart';

class PlayerScreen extends StatelessWidget {
  final String bookId;

  const PlayerScreen({super.key, required this.bookId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Player: $bookId'),
      ),
      body: const Center(
        child: Text('Player Screen'),
      ),
    );
  }
}