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

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

/// Widget for displaying audiobook information (cover, title, author).
///
/// This is a pure UI widget that displays information.
/// All business logic should be handled by the parent widget or provider.
class PlayerInfo extends StatelessWidget {
  /// Creates a new PlayerInfo instance.
  const PlayerInfo({
    super.key,
    required this.title,
    required this.author,
    this.coverUrl,
    this.category,
    this.size,
  });

  /// Book title.
  final String title;

  /// Book author.
  final String author;

  /// Cover image URL.
  final String? coverUrl;

  /// Book category.
  final String? category;

  /// Book size.
  final String? size;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Cover image if available
            if (coverUrl != null && coverUrl!.isNotEmpty)
              RepaintBoundary(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: CachedNetworkImage(
                    imageUrl: coverUrl!,
                    width: 96,
                    height: 96,
                    fit: BoxFit.cover,
                    placeholder: (context, url) => Container(
                      width: 96,
                      height: 96,
                      color:
                          Theme.of(context).colorScheme.surfaceContainerHighest,
                      child: const Center(
                        child: CircularProgressIndicator(),
                      ),
                    ),
                    errorWidget: (context, url, error) => Container(
                      width: 96,
                      height: 96,
                      color: Theme.of(context).colorScheme.errorContainer,
                      child: Icon(
                        Icons.error_outline,
                        color: Theme.of(context).colorScheme.onErrorContainer,
                      ),
                    ),
                  ),
                ),
              ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'by $author',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (category != null || size != null) ...[
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        if (category != null)
                          Chip(
                            label: Text(category!),
                            backgroundColor: Colors.blue.shade100,
                          ),
                        if (category != null && size != null)
                          const SizedBox(width: 8),
                        if (size != null)
                          Chip(
                            label: Text(size!),
                            backgroundColor: Colors.green.shade100,
                          ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      );
}
