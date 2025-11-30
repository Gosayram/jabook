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

import 'package:path/path.dart' as path;

/// Represents a local audiobook file found on the device.
///
/// This is a domain entity that contains information about an audiobook file
/// that was scanned from the file system, including its path, size, and metadata.
class LocalAudiobook {
  /// Creates a new LocalAudiobook instance.
  LocalAudiobook({
    required this.filePath,
    required this.fileName,
    required this.fileSize,
    this.title,
    this.author,
    this.duration,
    this.coverPath,
    this.scannedAt,
  });

  /// Full path to the audiobook file.
  final String filePath;

  /// Name of the file (without path).
  final String fileName;

  /// Size of the file in bytes.
  final int fileSize;

  /// Title of the audiobook (extracted from metadata or filename).
  final String? title;

  /// Author of the audiobook (extracted from metadata or filename).
  final String? author;

  /// Duration of the audiobook in milliseconds (if available).
  final int? duration;

  /// Path to the cover image (if available).
  final String? coverPath;

  /// Timestamp when this audiobook was scanned.
  final DateTime? scannedAt;

  /// Gets the file extension.
  String get extension {
    final parts = fileName.split('.');
    if (parts.length > 1) {
      return parts.last.toLowerCase();
    }
    return '';
  }

  /// Gets a display name for the audiobook.
  ///
  /// Uses title if available, otherwise uses filename without extension.
  String get displayName {
    if (title != null && title!.isNotEmpty) {
      return title!;
    }
    // Remove extension from filename
    final nameWithoutExt = fileName;
    final lastDot = nameWithoutExt.lastIndexOf('.');
    if (lastDot > 0) {
      return nameWithoutExt.substring(0, lastDot);
    }
    return nameWithoutExt;
  }

  /// Gets the folder name (part name) for this file relative to groupPath.
  ///
  /// Returns null if the file is directly in groupPath (no subfolder).
  /// Returns the subfolder name if the file is in a subfolder.
  ///
  /// The [groupPath] parameter is the base path of the group.
  String? getPartFolderName(String groupPath) {
    try {
      final normalizedFilePath = path.normalize(filePath);
      final normalizedGroupPath = path.normalize(groupPath);

      if (!normalizedFilePath.startsWith(normalizedGroupPath)) {
        return null;
      }

      // Get relative path from groupPath
      final relativePath =
          path.relative(normalizedFilePath, from: normalizedGroupPath);
      final parts = path.split(relativePath);

      // If there's more than just the filename, return the first folder name
      if (parts.length > 1) {
        return parts[0];
      }

      return null;
    } on Exception {
      return null;
    }
  }

  /// Gets display name with part folder prefix if applicable.
  ///
  /// The [groupPath] parameter is the base path of the group.
  /// Returns display name with part folder prefix if file is in a subfolder,
  /// otherwise returns just the display name.
  String getDisplayNameWithPart(String groupPath) {
    final partName = getPartFolderName(groupPath);
    if (partName != null && partName.isNotEmpty) {
      return '$partName — $displayName';
    }
    return displayName;
  }

  /// Gets formatted file size (e.g., "1.5 MB").
  String get formattedSize {
    if (fileSize < 1024) {
      return '$fileSize B';
    } else if (fileSize < 1024 * 1024) {
      return '${(fileSize / 1024).toStringAsFixed(1)} KB';
    } else if (fileSize < 1024 * 1024 * 1024) {
      return '${(fileSize / (1024 * 1024)).toStringAsFixed(1)} MB';
    } else {
      return '${(fileSize / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
    }
  }

  /// Creates a copy of this LocalAudiobook with updated values.
  LocalAudiobook copyWith({
    String? filePath,
    String? fileName,
    int? fileSize,
    String? title,
    String? author,
    int? duration,
    String? coverPath,
    DateTime? scannedAt,
  }) =>
      LocalAudiobook(
        filePath: filePath ?? this.filePath,
        fileName: fileName ?? this.fileName,
        fileSize: fileSize ?? this.fileSize,
        title: title ?? this.title,
        author: author ?? this.author,
        duration: duration ?? this.duration,
        coverPath: coverPath ?? this.coverPath,
        scannedAt: scannedAt ?? this.scannedAt,
      );

  @override
  String toString() =>
      'LocalAudiobook{filePath: $filePath, fileName: $fileName, fileSize: $fileSize, title: $title}';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LocalAudiobook &&
          runtimeType == other.runtimeType &&
          filePath == other.filePath;

  @override
  int get hashCode => filePath.hashCode;
}
