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

import 'package:jabook/core/domain/library/entities/local_audiobook.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/library/local_audiobook.dart' as old_library;

/// Mapper for converting old library entities to new domain entities.
class LibraryMapper {
  // Private constructor to prevent instantiation
  LibraryMapper._();

  /// Converts old LocalAudiobook to new domain LocalAudiobook.
  static LocalAudiobook toDomain(old_library.LocalAudiobook old) =>
      LocalAudiobook(
        filePath: old.filePath,
        fileName: old.fileName,
        fileSize: old.fileSize,
        title: old.title,
        author: old.author,
        duration: old.duration,
        coverPath: old.coverPath,
        scannedAt: old.scannedAt,
      );

  /// Converts list of old LocalAudiobook to list of new domain LocalAudiobook.
  static List<LocalAudiobook> toDomainList(
    List<old_library.LocalAudiobook> oldList,
  ) =>
      oldList.map(toDomain).toList();

  /// Converts old LocalAudiobookGroup to new domain LocalAudiobookGroup.
  static LocalAudiobookGroup toDomainGroup(
    old_library.LocalAudiobookGroup old,
  ) =>
      LocalAudiobookGroup(
        groupName: old.groupName,
        groupPath: old.groupPath,
        files: toDomainList(old.files),
        torrentId: old.torrentId,
        coverPath: old.coverPath,
        scannedAt: old.scannedAt,
      );

  /// Converts list of old LocalAudiobookGroup to list of new domain LocalAudiobookGroup.
  static List<LocalAudiobookGroup> toDomainGroupList(
    List<old_library.LocalAudiobookGroup> oldList,
  ) =>
      oldList.map(toDomainGroup).toList();
}
