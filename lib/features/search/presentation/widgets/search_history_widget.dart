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

import 'package:flutter/material.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Widget for displaying search history.
///
/// Shows a list of previous search queries that user can tap to repeat.
class SearchHistoryWidget extends StatelessWidget {
  /// Creates a new SearchHistoryWidget instance.
  const SearchHistoryWidget({
    required this.history,
    required this.onQuerySelected,
    required this.onQueryRemoved,
    required this.onClear,
    super.key,
  });

  /// List of search history queries.
  final List<String> history;

  /// Callback when a query is selected.
  final void Function(String query) onQuerySelected;

  /// Callback when a query is removed.
  final void Function(String query) onQueryRemoved;

  /// Callback when clear is requested.
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) => Container(
        constraints: const BoxConstraints(maxHeight: 300),
        margin: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(8),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.1),
              blurRadius: 4,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(12.0),
              child: Row(
                children: [
                  Text(
                    AppLocalizations.of(context)?.searchHistoryTitle ??
                        'Search History',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const Spacer(),
                  if (history.isNotEmpty)
                    TextButton(
                      onPressed: onClear,
                      child: Text(
                        AppLocalizations.of(context)?.clearButton ?? 'Clear',
                      ),
                    ),
                ],
              ),
            ),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: history.length,
                itemBuilder: (context, index) {
                  final query = history[index];
                  return ListTile(
                    leading: const Icon(Icons.history, size: 20),
                    title: Text(query),
                    trailing: IconButton(
                      icon: const Icon(Icons.close, size: 18),
                      onPressed: () => onQueryRemoved(query),
                    ),
                    onTap: () => onQuerySelected(query),
                  );
                },
              ),
            ),
          ],
        ),
      );
}
