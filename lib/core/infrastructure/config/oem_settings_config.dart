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

/// Configuration for OEM-specific settings paths.
///
/// This class provides a structure for storing and loading OEM settings
/// configuration from remote sources (e.g., JSON from GitHub/CDN) or
/// embedded local configuration.
///
/// Future enhancement: Load from remote config to update paths without app release.
class OemSettingsConfig {
  /// Creates OemSettingsConfig from JSON.
  factory OemSettingsConfig.fromJson(Map<String, dynamic> json) {
    final manufacturers = <String, ManufacturerConfig>{};
    if (json['manufacturers'] != null) {
      final manufacturersJson = json['manufacturers'] as Map<String, dynamic>;
      for (final entry in manufacturersJson.entries) {
        manufacturers[entry.key] = ManufacturerConfig.fromJson(entry.value);
      }
    }

    return OemSettingsConfig(
      version: json['version'] as int? ?? 1,
      manufacturers: manufacturers,
    );
  }

  /// Creates a new OemSettingsConfig.
  const OemSettingsConfig({
    required this.version,
    required this.manufacturers,
  });

  /// Configuration version.
  final int version;

  /// Map of manufacturer configurations.
  final Map<String, ManufacturerConfig> manufacturers;

  /// Converts OemSettingsConfig to JSON.
  Map<String, dynamic> toJson() => {
        'version': version,
        'manufacturers': manufacturers.map(
          (key, value) => MapEntry(key, value.toJson()),
        ),
      };

  /// Gets configuration for a specific manufacturer.
  ///
  /// Returns null if manufacturer is not found.
  ManufacturerConfig? getManufacturerConfig(String manufacturer) {
    final normalized = manufacturer.toLowerCase();
    return manufacturers[normalized];
  }

  /// Default embedded configuration (fallback if remote config fails).
  static const OemSettingsConfig defaultConfig = OemSettingsConfig(
    version: 1,
    manufacturers: {
      // Default configurations are handled in ManufacturerSettingsHelper.kt
      // This is a placeholder for future remote config support
    },
  );
}

/// Configuration for a specific manufacturer.
class ManufacturerConfig {
  /// Creates ManufacturerConfig from JSON.
  factory ManufacturerConfig.fromJson(Map<String, dynamic> json) =>
      ManufacturerConfig(
        name: json['name'] as String? ?? '',
        autostart: (json['autostart'] as List<dynamic>?)
                ?.map((e) => IntentPath.fromJson(e as Map<String, dynamic>))
                .toList() ??
            [],
        batteryOptimization: (json['batteryOptimization'] as List<dynamic>?)
                ?.map((e) => IntentPath.fromJson(e as Map<String, dynamic>))
                .toList() ??
            [],
        backgroundRestrictions:
            (json['backgroundRestrictions'] as List<dynamic>?)
                    ?.map((e) => IntentPath.fromJson(e as Map<String, dynamic>))
                    .toList() ??
                [],
      );

  /// Creates a new ManufacturerConfig.
  const ManufacturerConfig({
    required this.name,
    required this.autostart,
    required this.batteryOptimization,
    required this.backgroundRestrictions,
  });

  /// Manufacturer name.
  final String name;

  /// Autostart settings paths.
  final List<IntentPath> autostart;

  /// Battery optimization settings paths.
  final List<IntentPath> batteryOptimization;

  /// Background restrictions settings paths.
  final List<IntentPath> backgroundRestrictions;

  /// Converts ManufacturerConfig to JSON.
  Map<String, dynamic> toJson() => {
        'name': name,
        'autostart': autostart.map((e) => e.toJson()).toList(),
        'batteryOptimization':
            batteryOptimization.map((e) => e.toJson()).toList(),
        'backgroundRestrictions':
            backgroundRestrictions.map((e) => e.toJson()).toList(),
      };
}

/// Intent path configuration for opening OEM settings.
class IntentPath {
  /// Creates IntentPath from JSON.
  factory IntentPath.fromJson(Map<String, dynamic> json) => IntentPath(
        packageName: json['packageName'] as String? ?? '',
        activityName: json['activityName'] as String? ?? '',
        extra: json['extra'] as Map<String, dynamic>?,
        minSdk: json['minSdk'] as int?,
        maxSdk: json['maxSdk'] as int?,
        romVersion: json['romVersion'] as String?,
      );

  /// Creates a new IntentPath.
  const IntentPath({
    required this.packageName,
    required this.activityName,
    this.extra,
    this.minSdk,
    this.maxSdk,
    this.romVersion,
  });

  /// Package name for the Intent.
  final String packageName;

  /// Activity name for the Intent.
  final String activityName;

  /// Extra data for the Intent (optional).
  final Map<String, dynamic>? extra;

  /// Minimum SDK version (optional).
  final int? minSdk;

  /// Maximum SDK version (optional).
  final int? maxSdk;

  /// ROM version requirement (optional, e.g., "MIUI 14+").
  final String? romVersion;

  /// Converts IntentPath to JSON.
  Map<String, dynamic> toJson() => {
        'packageName': packageName,
        'activityName': activityName,
        if (extra != null) 'extra': extra,
        if (minSdk != null) 'minSdk': minSdk,
        if (maxSdk != null) 'maxSdk': maxSdk,
        if (romVersion != null) 'romVersion': romVersion,
      };
}

/// Service for loading OEM settings configuration.
///
/// Future enhancement: Load from remote config (GitHub/CDN) with caching.
class OemSettingsConfigService {
  OemSettingsConfigService._();

  /// Singleton instance.
  static final OemSettingsConfigService instance = OemSettingsConfigService._();

  /// Currently loaded configuration.
  OemSettingsConfig? _config;

  /// Loads configuration from remote source.
  ///
  /// Future implementation: Fetch from remote config URL with caching.
  /// For now, returns default embedded configuration.
  Future<OemSettingsConfig> loadConfig({String? remoteUrl}) async {
    // TODO: Implement remote config loading
    // 1. Check cache (SharedPreferences)
    // 2. Fetch from remote URL if cache is stale or missing
    // 3. Parse JSON and create OemSettingsConfig
    // 4. Cache the result
    // 5. Return configuration

    if (_config != null) {
      return _config!;
    }

    _config = OemSettingsConfig.defaultConfig;
    return _config!;
  }

  /// Gets configuration for a specific manufacturer.
  Future<ManufacturerConfig?> getManufacturerConfig(String manufacturer) async {
    final config = await loadConfig();
    return config.getManufacturerConfig(manufacturer);
  }

  /// Clears cached configuration (forces reload on next access).
  void clearCache() {
    _config = null;
  }
}
