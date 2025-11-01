/// Represents an audiobook entity from RuTracker
class Audiobook {
  /// Creates a new Audiobook instance.
  Audiobook({
    required this.id,
    required this.title,
    required this.author,
    required this.category,
    required this.size,
    required this.seeders,
    required this.leechers,
    required this.magnetUrl,
    this.coverUrl,
    required this.chapters,
    required this.addedDate,
  });

  /// Unique identifier of the audiobook (topic ID)
  final String id;

  /// Title of the audiobook
  final String title;

  /// Author of the audiobook
  final String author;

  /// Category of the audiobook
  final String category;

  /// File size of the audiobook
  final String size;

  /// Number of seeders
  final int seeders;

  /// Number of leechers
  final int leechers;

  /// Magnet URL for downloading
  final String magnetUrl;

  /// URL to the cover image
  final String? coverUrl;

  /// List of chapters in the audiobook
  final List<Chapter> chapters;

  /// Date when the audiobook was added to RuTracker
  final DateTime addedDate;

  /// Creates a copy of this audiobook with updated values.
  Audiobook copyWith({
    String? id,
    String? title,
    String? author,
    String? category,
    String? size,
    int? seeders,
    int? leechers,
    String? magnetUrl,
    String? coverUrl,
    List<Chapter>? chapters,
    DateTime? addedDate,
  }) =>
      Audiobook(
        id: id ?? this.id,
        title: title ?? this.title,
        author: author ?? this.author,
        category: category ?? this.category,
        size: size ?? this.size,
        seeders: seeders ?? this.seeders,
        leechers: leechers ?? this.leechers,
        magnetUrl: magnetUrl ?? this.magnetUrl,
        coverUrl: coverUrl ?? this.coverUrl,
        chapters: chapters ?? this.chapters,
        addedDate: addedDate ?? this.addedDate,
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Audiobook && runtimeType == other.runtimeType && id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  String toString() =>
      'Audiobook{id: $id, title: $title, author: $author, category: $category, size: $size, seeders: $seeders, leechers: $leechers}';
}

/// Represents a chapter in an audiobook
class Chapter {
  /// Creates a new Chapter instance.
  Chapter({
    required this.title,
    required this.durationMs,
    required this.fileIndex,
    required this.startByte,
    required this.endByte,
  });

  /// Title of the chapter
  final String title;

  /// Duration of the chapter in milliseconds
  final int durationMs;

  /// Index of the file containing this chapter
  final int fileIndex;

  /// Starting byte position in the file
  final int startByte;

  /// Ending byte position in the file
  final int endByte;

  /// Creates a copy of this chapter with updated values.
  Chapter copyWith({
    String? title,
    int? durationMs,
    int? fileIndex,
    int? startByte,
    int? endByte,
  }) =>
      Chapter(
        title: title ?? this.title,
        durationMs: durationMs ?? this.durationMs,
        fileIndex: fileIndex ?? this.fileIndex,
        startByte: startByte ?? this.startByte,
        endByte: endByte ?? this.endByte,
      );

  @override
  String toString() =>
      'Chapter{title: $title, durationMs: $durationMs, fileIndex: $fileIndex}';
}
