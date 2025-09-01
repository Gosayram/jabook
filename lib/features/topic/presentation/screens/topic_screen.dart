import 'package:flutter/material.dart';

/// Screen for displaying a specific RuTracker topic.
///
/// This screen shows the details of a specific forum topic,
/// including posts, attachments, and download links.
class TopicScreen extends StatelessWidget {

  /// Creates a new TopicScreen instance.
  ///
  /// The [topicId] parameter is required to identify which topic
  /// should be displayed.
  const TopicScreen({super.key, required this.topicId});
  
  /// The unique identifier of the topic to display.
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