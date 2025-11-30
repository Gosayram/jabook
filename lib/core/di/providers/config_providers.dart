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

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/infrastructure/config/app_config.dart';
import 'package:jabook/core/infrastructure/config/audio_settings_manager.dart';
import 'package:jabook/core/infrastructure/config/language_manager.dart';
import 'package:jabook/core/infrastructure/config/theme_manager.dart';

/// Provider for AppConfig instance.
///
/// This provider creates an AppConfig instance that can be used
/// throughout the application for environment-specific configuration.
final appConfigProvider = Provider<AppConfig>((ref) => AppConfig());

/// Provider for LanguageManager instance.
///
/// This provider creates a LanguageManager instance that can be used
/// throughout the application for language settings management.
final languageManagerProvider =
    Provider<LanguageManager>((ref) => LanguageManager());

/// Provider for AudioSettingsManager instance.
///
/// This provider creates an AudioSettingsManager instance that can be used
/// throughout the application for audio settings management.
final audioSettingsManagerProvider =
    Provider<AudioSettingsManager>((ref) => AudioSettingsManager());

/// Provider for ThemeManager instance.
///
/// This provider creates a ThemeManager instance that can be used
/// throughout the application for theme management.
final themeManagerProvider = Provider<ThemeManager>((ref) => ThemeManager());
