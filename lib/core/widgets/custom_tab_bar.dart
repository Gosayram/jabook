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
import 'package:jabook/core/utils/color_scheme_helper.dart';

/// Custom TabBar widget with improved visualization.
///
/// This widget provides a custom TabBar with:
/// - Clear active tab highlighting
/// - Smooth animations
/// - Improved accessibility
/// - Support for prod/beta color schemes
class CustomTabBar extends StatelessWidget implements PreferredSizeWidget {
  /// Creates a new CustomTabBar instance.
  const CustomTabBar({
    required this.controller,
    required this.tabs,
    this.isBeta = false,
    super.key,
  });

  /// TabController for managing tab state.
  final TabController controller;

  /// List of tabs to display.
  final List<Tab> tabs;

  /// Whether the app is in beta mode.
  final bool isBeta;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = ColorSchemeHelper.getPrimaryColor(isBeta);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.scaffoldBackgroundColor,
        border: Border(
          bottom: BorderSide(
            color: theme.dividerColor.withValues(alpha: 0.1),
          ),
        ),
      ),
      child: TabBar(
        controller: controller,
        tabs: tabs,
        indicator: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          color: primaryColor.withValues(alpha: 0.1),
        ),
        indicatorSize: TabBarIndicatorSize.tab,
        labelColor: primaryColor,
        unselectedLabelColor:
            theme.colorScheme.onSurface.withValues(alpha: 0.6),
        labelStyle: const TextStyle(
          fontFamily: 'Inter',
          fontSize: 14,
          fontWeight: FontWeight.w600,
        ),
        unselectedLabelStyle: const TextStyle(
          fontFamily: 'Inter',
          fontSize: 14,
          fontWeight: FontWeight.normal,
        ),
        dividerColor: Colors.transparent,
      ),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(48);
}
