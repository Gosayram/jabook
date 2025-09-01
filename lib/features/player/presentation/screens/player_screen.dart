import 'package:flutter/material.dart';

class PlayerScreen extends StatelessWidget {

  const PlayerScreen({super.key, required this.bookId});
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