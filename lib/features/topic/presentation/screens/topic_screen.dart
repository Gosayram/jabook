import 'package:flutter/material.dart';

class TopicScreen extends StatelessWidget {

  const TopicScreen({super.key, required this.topicId});
  final String topicId;

  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(
        title: Text('Topic: $topicId'),
      ),
      body: const Center(
        child: Text('Topic Screen'),
      ),
    );
}