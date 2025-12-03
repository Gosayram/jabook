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

/// Accessibility settings section widget.
class AccessibilitySection extends StatelessWidget {
  /// Creates a new AccessibilitySection instance.
  const AccessibilitySection({
    super.key,
    required this.reduceAnimations,
    required this.onReduceAnimationsChanged,
  });

  /// Whether animations are reduced.
  final bool reduceAnimations;

  /// Callback when reduce animations setting is changed.
  final void Function(bool value) onReduceAnimationsChanged;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Accessibility',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            'Customize accessibility options',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          // Reduce animations toggle
          ListTile(
            leading: const Icon(Icons.animation),
            title: const Text('Reduce Animations'),
            subtitle: Text(
              'Disable complex animations to improve performance and save battery',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            trailing: Semantics(
              label: 'Reduce animations toggle',
              child: Switch(
                value: reduceAnimations,
                onChanged: onReduceAnimationsChanged,
              ),
            ),
          ),
        ],
      );
}
