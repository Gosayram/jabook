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

/// Settings for smart search cache.
///
/// Defines cache TTL, auto-update settings, and timing information.
class CacheSettings {
  /// Default cache settings constructor.
  ///
  /// - TTL: 1 week (168 hours)
  /// - Auto-update: enabled
  /// - Auto-update interval: 1 week (168 hours)
  CacheSettings.standard()
      : cacheTTL = const Duration(hours: 168),
        autoUpdateEnabled = true,
        autoUpdateInterval = const Duration(hours: 168),
        lastUpdateTime = null,
        nextUpdateTime = null;

  /// Creates CacheSettings from a Map.
  factory CacheSettings.fromMap(Map<String, dynamic> map) {
    final cacheTTLHours = map['cache_ttl_hours'] as int? ?? 168;
    final autoUpdateIntervalHours =
        map['auto_update_interval_hours'] as int? ?? 168;

    return CacheSettings(
      cacheTTL: Duration(hours: cacheTTLHours),
      autoUpdateEnabled: map['auto_update_enabled'] as bool? ?? true,
      autoUpdateInterval: Duration(hours: autoUpdateIntervalHours),
      lastUpdateTime: map['last_update_time'] != null
          ? DateTime.parse(map['last_update_time'] as String)
          : null,
      nextUpdateTime: map['next_update_time'] != null
          ? DateTime.parse(map['next_update_time'] as String)
          : null,
    );
  }

  /// Creates a new CacheSettings instance.
  CacheSettings({
    required this.cacheTTL,
    required this.autoUpdateEnabled,
    required this.autoUpdateInterval,
    this.lastUpdateTime,
    this.nextUpdateTime,
  }) : assert(
          cacheTTL.inHours >= 12,
          'Cache TTL must be at least 12 hours',
        );

  /// Time to live for cache entries (minimum 12 hours).
  final Duration cacheTTL;

  /// Whether automatic updates are enabled.
  final bool autoUpdateEnabled;

  /// Interval between automatic updates.
  final Duration autoUpdateInterval;

  /// Time of last cache update.
  final DateTime? lastUpdateTime;

  /// Time of next scheduled update.
  final DateTime? nextUpdateTime;

  /// Creates a copy of this settings with updated values.
  CacheSettings copyWith({
    Duration? cacheTTL,
    bool? autoUpdateEnabled,
    Duration? autoUpdateInterval,
    DateTime? lastUpdateTime,
    DateTime? nextUpdateTime,
  }) =>
      CacheSettings(
        cacheTTL: cacheTTL ?? this.cacheTTL,
        autoUpdateEnabled: autoUpdateEnabled ?? this.autoUpdateEnabled,
        autoUpdateInterval: autoUpdateInterval ?? this.autoUpdateInterval,
        lastUpdateTime: lastUpdateTime ?? this.lastUpdateTime,
        nextUpdateTime: nextUpdateTime ?? this.nextUpdateTime,
      );

  /// Converts this settings to a Map for storage.
  Map<String, dynamic> toMap() => {
        'cache_ttl_hours': cacheTTL.inHours,
        'auto_update_enabled': autoUpdateEnabled,
        'auto_update_interval_hours': autoUpdateInterval.inHours,
        'last_update_time': lastUpdateTime?.toIso8601String(),
        'next_update_time': nextUpdateTime?.toIso8601String(),
      };

  @override
  String toString() => 'CacheSettings(cacheTTL: ${cacheTTL.inHours}h, '
      'autoUpdateEnabled: $autoUpdateEnabled, '
      'autoUpdateInterval: ${autoUpdateInterval.inHours}h, '
      'lastUpdateTime: $lastUpdateTime, '
      'nextUpdateTime: $nextUpdateTime)';
}
