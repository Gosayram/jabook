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

/// Widget for displaying category filters in search screen.
///
/// Allows users to filter search results by category.
class SearchCategoryFilters extends StatelessWidget {
  /// Creates a new SearchCategoryFilters instance.
  const SearchCategoryFilters({
    required this.availableCategories,
    required this.selectedCategories,
    required this.onCategoryToggled,
    required this.onReset,
    super.key,
  });

  /// List of available categories.
  final Set<String> availableCategories;

  /// Set of selected categories.
  final Set<String> selectedCategories;

  /// Callback when a category is toggled.
  final void Function(String category, bool selected) onCategoryToggled;

  /// Callback when reset is requested.
  final VoidCallback onReset;

  @override
  Widget build(BuildContext context) {
    if (availableCategories.isEmpty) {
      return const SizedBox.shrink();
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                AppLocalizations.of(context)?.filtersLabel ?? 'Filters:',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
              if (selectedCategories.isNotEmpty) ...[
                const SizedBox(width: 8),
                OutlinedButton.icon(
                  onPressed: onReset,
                  icon: const Icon(Icons.clear, size: 16),
                  label: Text(
                    AppLocalizations.of(context)?.resetButton ?? 'Reset',
                  ),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    minimumSize: Size.zero,
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: availableCategories.map((category) {
              final isSelected = selectedCategories.contains(category);
              return FilterChip(
                label: Text(
                  category,
                  style: TextStyle(
                    fontWeight:
                        isSelected ? FontWeight.w600 : FontWeight.normal,
                  ),
                ),
                selected: isSelected,
                selectedColor: Theme.of(context).colorScheme.primaryContainer,
                checkmarkColor:
                    Theme.of(context).colorScheme.onPrimaryContainer,
                onSelected: (selected) => onCategoryToggled(category, selected),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}
