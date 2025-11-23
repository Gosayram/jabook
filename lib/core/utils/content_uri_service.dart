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

import 'dart:io';

import 'package:flutter/services.dart';

/// Represents a file or directory entry from ContentResolver.
class ContentUriEntry {
  /// Creates a ContentUriEntry from a map.
  ContentUriEntry.fromMap(Map<dynamic, dynamic> map)
      : uri = map['uri'] as String,
        name = map['name'] as String,
        mimeType = map['mimeType'] as String,
        size = int.tryParse(map['size']?.toString() ?? '0') ?? 0,
        isDirectory = map['isDirectory']?.toString().toLowerCase() == 'true';

  /// The content URI of the entry.
  final String uri;

  /// The display name of the entry.
  final String name;

  /// The MIME type of the entry.
  final String mimeType;

  /// The size of the entry in bytes.
  final int size;

  /// Whether this entry is a directory.
  final bool isDirectory;
}

/// Service for accessing files via ContentResolver on Android.
///
/// This service provides methods to work with content:// URIs
/// which are required for accessing files on Android 7+ (API 24+)
/// when using Scoped Storage.
class ContentUriService {
  /// Creates a new ContentUriService instance.
  ContentUriService();

  static const MethodChannel _channel = MethodChannel('content_uri_channel');

  /// Lists files and directories in a content URI.
  ///
  /// The [uri] parameter is the content URI to list.
  ///
  /// Returns a list of ContentUriEntry instances.
  Future<List<ContentUriEntry>> listDirectory(String uri) async {
    if (!Platform.isAndroid) {
      throw UnsupportedError('ContentUriService is only supported on Android');
    }

    try {
      final result = await _channel.invokeMethod<List<dynamic>>(
        'listDirectory',
        {'uri': uri},
      );

      if (result == null) {
        return [];
      }

      return result
          .map((item) => ContentUriEntry.fromMap(item as Map<dynamic, dynamic>))
          .toList();
    } on PlatformException catch (e) {
      throw Exception('Failed to list directory: ${e.message}');
    }
  }

  /// Checks if the app has access to a content URI.
  ///
  /// The [uri] parameter is the content URI to check.
  ///
  /// Returns true if the app has access, false otherwise.
  Future<bool> checkUriAccess(String uri) async {
    if (!Platform.isAndroid) {
      return false;
    }

    try {
      final result = await _channel.invokeMethod<bool>(
        'checkUriAccess',
        {'uri': uri},
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw Exception('Failed to check URI access: ${e.message}');
    }
  }
}
