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

import 'package:jabook/core/domain/downloads/entities/download_item.dart';

/// Mapper for converting between data models and domain entities for downloads.
class DownloadsMapper {
  // Private constructor to prevent instantiation
  DownloadsMapper._();

  /// Converts a map (from AudiobookTorrentManager) to a DownloadItem domain entity.
  ///
  /// The [data] parameter is a map containing download information.
  ///
  /// Returns a DownloadItem instance.
  static DownloadItem toDomain(Map<String, dynamic> data) => DownloadItem(
        id: data['id'] as String? ?? '',
        name: data['name'] as String? ?? 'Unknown',
        title: data['title'] as String?,
        status: data['status'] as String? ?? 'unknown',
        progress: (data['progress'] as num?)?.toDouble() ?? 0.0,
        downloadSpeed: (data['downloadSpeed'] as num?)?.toDouble() ?? 0.0,
        uploadSpeed: (data['uploadSpeed'] as num?)?.toDouble() ?? 0.0,
        downloadedBytes: (data['downloadedBytes'] as num?)?.toInt() ?? 0,
        totalBytes: (data['totalBytes'] as num?)?.toInt() ?? 0,
        seeders: (data['seeders'] as num?)?.toInt() ?? 0,
        leechers: (data['leechers'] as num?)?.toInt() ?? 0,
        isActive: data['isActive'] as bool? ?? false,
        savePath: data['savePath'] as String?,
        startedAt: data['startedAt'] as DateTime?,
        pausedAt: data['pausedAt'] as DateTime?,
      );

  /// Converts a list of maps to a list of DownloadItem domain entities.
  ///
  /// The [dataList] parameter is a list of maps containing download information.
  ///
  /// Returns a list of DownloadItem instances.
  static List<DownloadItem> toDomainList(List<Map<String, dynamic>> dataList) =>
      dataList.map(toDomain).toList();
}
