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

import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Model for individual book audio settings.
class BookAudioSettings {
  /// Creates BookAudioSettings from JSON map.
  factory BookAudioSettings.fromJson(Map<String, dynamic> json) =>
      BookAudioSettings(
        playbackSpeed: (json['playbackSpeed'] as num?)?.toDouble(),
        rewindDuration: json['rewindDuration'] as int?,
        forwardDuration: json['forwardDuration'] as int?,
        volumeBoostLevel: json['volumeBoostLevel'] as String?,
        drcLevel: json['drcLevel'] as String?,
        speechEnhancer: json['speechEnhancer'] as bool?,
        autoVolumeLeveling: json['autoVolumeLeveling'] as bool?,
        normalizeVolume: json['normalizeVolume'] as bool?,
      );

  /// Creates a new BookAudioSettings instance.
  const BookAudioSettings({
    this.playbackSpeed,
    this.rewindDuration,
    this.forwardDuration,
    this.volumeBoostLevel,
    this.drcLevel,
    this.speechEnhancer,
    this.autoVolumeLeveling,
    this.normalizeVolume,
  });

  /// Playback speed for this book.
  final double? playbackSpeed;

  /// Rewind duration in seconds for this book.
  final int? rewindDuration;

  /// Forward duration in seconds for this book.
  final int? forwardDuration;

  /// Volume boost level for this book (Off, Boost50, Boost100, Boost200, Auto).
  final String? volumeBoostLevel;

  /// DRC (Dynamic Range Compression) level for this book (Off, Gentle, Medium, Strong).
  final String? drcLevel;

  /// Whether speech enhancer is enabled for this book.
  final bool? speechEnhancer;

  /// Whether auto volume leveling is enabled for this book.
  final bool? autoVolumeLeveling;

  /// Whether volume normalization is enabled for this book.
  final bool? normalizeVolume;

  /// Creates a copy with updated fields.
  BookAudioSettings copyWith({
    double? playbackSpeed,
    int? rewindDuration,
    int? forwardDuration,
    String? volumeBoostLevel,
    String? drcLevel,
    bool? speechEnhancer,
    bool? autoVolumeLeveling,
    bool? normalizeVolume,
  }) =>
      BookAudioSettings(
        playbackSpeed: playbackSpeed ?? this.playbackSpeed,
        rewindDuration: rewindDuration ?? this.rewindDuration,
        forwardDuration: forwardDuration ?? this.forwardDuration,
        volumeBoostLevel: volumeBoostLevel ?? this.volumeBoostLevel,
        drcLevel: drcLevel ?? this.drcLevel,
        speechEnhancer: speechEnhancer ?? this.speechEnhancer,
        autoVolumeLeveling: autoVolumeLeveling ?? this.autoVolumeLeveling,
        normalizeVolume: normalizeVolume ?? this.normalizeVolume,
      );

  /// Converts to JSON map.
  Map<String, dynamic> toJson() => {
        if (playbackSpeed != null) 'playbackSpeed': playbackSpeed,
        if (rewindDuration != null) 'rewindDuration': rewindDuration,
        if (forwardDuration != null) 'forwardDuration': forwardDuration,
        if (volumeBoostLevel != null) 'volumeBoostLevel': volumeBoostLevel,
        if (drcLevel != null) 'drcLevel': drcLevel,
        if (speechEnhancer != null) 'speechEnhancer': speechEnhancer,
        if (autoVolumeLeveling != null)
          'autoVolumeLeveling': autoVolumeLeveling,
        if (normalizeVolume != null) 'normalizeVolume': normalizeVolume,
      };
}

/// Service for managing individual book audio settings.
///
/// Stores settings per book using groupPath as identifier.
class BookAudioSettingsService {
  /// Key for storing book audio settings in SharedPreferences.
  static const String _settingsKey = 'book_audio_settings';

  /// Gets audio settings for a specific book.
  ///
  /// [bookId] is the groupPath or book identifier.
  /// Returns null if no settings exist for this book.
  Future<BookAudioSettings?> getSettings(String bookId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = prefs.getString(_settingsKey);
      if (json == null) return null;

      final map = jsonDecode(json) as Map<String, dynamic>;
      final bookSettings = map[bookId] as Map<String, dynamic>?;
      if (bookSettings == null) return null;

      return BookAudioSettings.fromJson(bookSettings);
    } on Exception {
      return null;
    }
  }

  /// Saves audio settings for a specific book.
  ///
  /// [bookId] is the groupPath or book identifier.
  /// [settings] are the settings to save.
  Future<void> saveSettings(
    String bookId,
    BookAudioSettings settings,
  ) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = prefs.getString(_settingsKey);
      final map = json != null
          ? jsonDecode(json) as Map<String, dynamic>
          : <String, dynamic>{};

      map[bookId] = settings.toJson();
      await prefs.setString(_settingsKey, jsonEncode(map));
    } on Exception {
      // Ignore save errors
    }
  }

  /// Updates specific settings for a book.
  ///
  /// Merges with existing settings if they exist.
  Future<void> updateSettings(
    String bookId,
    BookAudioSettings settings,
  ) async {
    final existing = await getSettings(bookId);
    final merged = existing != null
        ? existing.copyWith(
            playbackSpeed: settings.playbackSpeed ?? existing.playbackSpeed,
            rewindDuration: settings.rewindDuration ?? existing.rewindDuration,
            forwardDuration:
                settings.forwardDuration ?? existing.forwardDuration,
            volumeBoostLevel:
                settings.volumeBoostLevel ?? existing.volumeBoostLevel,
            drcLevel: settings.drcLevel ?? existing.drcLevel,
            speechEnhancer: settings.speechEnhancer ?? existing.speechEnhancer,
            autoVolumeLeveling:
                settings.autoVolumeLeveling ?? existing.autoVolumeLeveling,
            normalizeVolume:
                settings.normalizeVolume ?? existing.normalizeVolume,
          )
        : settings;
    await saveSettings(bookId, merged);
  }

  /// Removes settings for a specific book.
  Future<void> removeSettings(String bookId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = prefs.getString(_settingsKey);
      if (json == null) return;

      final map = jsonDecode(json) as Map<String, dynamic>..remove(bookId);
      await prefs.setString(_settingsKey, jsonEncode(map));
    } on Exception {
      // Ignore errors
    }
  }

  /// Gets all book settings.
  ///
  /// Returns a map of bookId to BookAudioSettings.
  Future<Map<String, BookAudioSettings>> getAllSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = prefs.getString(_settingsKey);
      if (json == null) return {};

      final map = jsonDecode(json) as Map<String, dynamic>;
      return map.map(
        (key, value) => MapEntry(
          key,
          BookAudioSettings.fromJson(value as Map<String, dynamic>),
        ),
      );
    } on Exception {
      return {};
    }
  }

  /// Clears all book settings.
  Future<void> clearAllSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_settingsKey);
    } on Exception {
      // Ignore errors
    }
  }
}
