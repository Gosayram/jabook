import 'package:flutter/material.dart';

class TopicScreen extends StatelessWidget {
  final String topicId;

  const TopicScreen({super.key, required this.topicId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Topic: $topicId'),
      ),
      body: const Center(
        child: Text('Topic Screen'),
      ),
    );
  }
}