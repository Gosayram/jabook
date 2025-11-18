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

/// Represents a category of audiobooks on RuTracker
class AudiobookCategory {
  /// Creates a new AudiobookCategory instance.
  AudiobookCategory({
    required this.id,
    required this.name,
    required this.url,
    this.subcategories = const [],
  });

  /// Unique identifier of the category (forum ID)
  final String id;

  /// Name of the category
  final String name;

  /// URL to the category page
  final String url;

  /// List of subcategories
  final List<AudiobookCategory> subcategories;

  /// Creates a copy of this category with updated values.
  AudiobookCategory copyWith({
    String? id,
    String? name,
    String? url,
    List<AudiobookCategory>? subcategories,
  }) =>
      AudiobookCategory(
        id: id ?? this.id,
        name: name ?? this.name,
        url: url ?? this.url,
        subcategories: subcategories ?? this.subcategories,
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AudiobookCategory &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  @override
  String toString() =>
      'AudiobookCategory{id: $id, name: $name, subcategories: ${subcategories.length}}';
}
