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
import 'package:jabook/app/router/app_router.dart';
import 'package:jabook/core/utils/color_scheme_helper.dart';

/// Custom BottomNavigationBar widget with improved visualization and animations.
///
/// This widget provides a custom bottom navigation bar with:
/// - Clear active tab highlighting with background
/// - Smooth animations
/// - Improved accessibility
/// - Support for prod/beta color schemes
class CustomBottomNavigationBar extends StatelessWidget {
  /// Creates a new CustomBottomNavigationBar instance.
  const CustomBottomNavigationBar({
    required this.currentIndex,
    required this.onTap,
    required this.items,
    this.isBeta = false,
    super.key,
  });

  /// Current selected index.
  final int currentIndex;

  /// Callback when a tab is tapped.
  final void Function(int) onTap;

  /// List of navigation items to display.
  final List<NavigationItem> items;

  /// Whether the app is in beta mode.
  final bool isBeta;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = ColorSchemeHelper.getPrimaryColor(isBeta);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.scaffoldBackgroundColor,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.1),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: SafeArea(
        child: Container(
          height: 70,
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: items.asMap().entries.map((entry) {
              final index = entry.key;
              final item = entry.value;
              final isSelected = index == currentIndex;

              return Expanded(
                child: Semantics(
                  label: item.title,
                  selected: isSelected,
                  child: GestureDetector(
                    onTap: () => onTap(index),
                    behavior: HitTestBehavior.opaque,
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      curve: Curves.easeInOut,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12),
                        color: isSelected
                            ? primaryColor.withValues(alpha: 0.1)
                            : Colors.transparent,
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Stack(
                            children: [
                              AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                padding: const EdgeInsets.all(4),
                                decoration: BoxDecoration(
                                  color: isSelected
                                      ? primaryColor
                                      : Colors.transparent,
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(
                                  item.icon,
                                  color: isSelected
                                      ? Colors.white
                                      : theme.colorScheme.onSurface
                                          .withValues(alpha: 0.6),
                                  size: 24,
                                ),
                              ),
                              if (item.badge != null)
                                Positioned(
                                  top: 0,
                                  right: 0,
                                  child: item.badge!,
                                ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          AnimatedDefaultTextStyle(
                            duration: const Duration(milliseconds: 200),
                            style: TextStyle(
                              fontFamily: 'Inter',
                              fontSize: 12,
                              fontWeight: isSelected
                                  ? FontWeight.w600
                                  : FontWeight.normal,
                              color: isSelected
                                  ? primaryColor
                                  : theme.colorScheme.onSurface
                                      .withValues(alpha: 0.6),
                            ),
                            child: Text(item.title),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }
}
