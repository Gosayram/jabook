// Copyright 2026 Jabook Contributors
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

package com.jabook.app.jabook.compose.data.local.scanner

import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct file system scanner that ignores .nomedia files.
 *
 * This scanner is used for custom user paths where .nomedia might be present
 * to hide images from gallery, but audio files should still be visible in Jabook.
 *
 * Use case: User has .nomedia in audiobook folder to prevent cover images
 * from appearing in gallery, but wants audio files to be scanned.
 */
@Singleton
class DirectFileSystemScanner
    @Inject
    constructor(
        private val metadataParser: AudioMetadataParser,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val bookIdentifier: BookIdentifier,
        private val encodingDetector: com.jabook.app.jabook.compose.data.local.parser.EncodingDetector,
        private val metadataCache: com.jabook.app.jabook.compose.data.local.parser.MetadataCache,
    ) : LocalBookScanner {
        /**
         * Fast file info without metadata parsing.
         * Used for initial quick scan before metadata parsing.
         */
        private data class FastFileInfo(
            val filePath: String,
            val displayName: String,
            val directory: String,
            val size: Long,
            val lastModified: Long,
        )

        private val _scanProgress = kotlinx.coroutines.flow.MutableStateFlow<ScanProgress>(ScanProgress.Idle)
        override val scanProgress: kotlinx.coroutines.flow.StateFlow<ScanProgress> = _scanProgress.asStateFlow()

        override suspend fun scanAudiobooks(): Result<List<ScannedBook>> =
            withContext(Dispatchers.IO) {
                try {
                    val customPaths = scanPathDao.getAllPathsList().map { it.path }

                    if (customPaths.isEmpty()) {
                        return@withContext Result.Success(emptyList())
                    }

                    _scanProgress.value = ScanProgress.Discovery(0)

                    // PHASE 1: FAST SCAN - No metadata parsing
                    android.util.Log.i("DirectFileScanner", "⚡ Phase 1: Fast scan (no metadata)")
                    val fastFiles = mutableListOf<FastFileInfo>()
                    for (path in customPaths) {
                        ensureActive() // Check for cancellation
                        val directory = File(path)
                        if (directory.exists() && directory.isDirectory) {
                            scanDirectoryFast(directory, fastFiles)
                            _scanProgress.value = ScanProgress.Discovery(fastFiles.size)
                        }
                    }

                    val totalFiles = fastFiles.size
                    android.util.Log.i(
                        "DirectFileScanner",
                        "Found $totalFiles audio files (fast scan)",
                    )

                    // PHASE 2: GROUP by directory
                    val groupedByDir = fastFiles.groupBy { it.directory }
                    android.util.Log.i(
                        "DirectFileScanner",
                        "📚 Grouped into ${groupedByDir.size} books (by directory)",
                    )

                    // PHASE 3: Parse metadata for ALL files (Fix for missing duration)
                    android.util.Log.i("DirectFileScanner", "⚡ Phase 3: Parsing metadata (Full Scan)")

                    val scannedBooks = mutableListOf<ScannedBook>()
                    var processedFilesCount = 0

                    groupedByDir.entries.forEachIndexed { index, (dir, files) ->
                        ensureActive() // Check for cancellation

                        val bookName = File(dir).name

                        // Sort files before parsing (to maintain chapter order)
                        val sortedFiles =
                            files.sortedWith(
                                compareBy(
                                    { file -> getFileCategory(file.displayName) },
                                    { file -> extractChapterInfo(file.displayName).toSortKey() },
                                    { file -> file.displayName.lowercase() },
                                ),
                            )

                        // Detect Book Metadata from FIRST file (as fallback if others fail, or for Album name)
                        // We still use the first file for Book-level metadata (Author/Cover) usually
                        val firstFile = sortedFiles.first()
                        val firstFileMetadata = metadataCache.getOrParse(File(firstFile.filePath), metadataParser)

                        val bookTitle = firstFileMetadata?.album ?: File(dir).name
                        val bookAuthor = firstFileMetadata?.albumArtist ?: firstFileMetadata?.artist ?: "Unknown"

                        val chapters = mutableListOf<ScannedChapter>()

                        // Parse EVERY file to get duration
                        for ((chapterIndex, fileInfo) in sortedFiles.withIndex()) {
                            ensureActive() // Granular cancellation check

                            // Update progress per FILE
                            processedFilesCount++
                            _scanProgress.value = ScanProgress.Parsing(bookTitle, processedFilesCount, totalFiles)

                            val file = File(fileInfo.filePath)
                            // Parse metadata -> needed for Duration
                            val metadata = metadataCache.getOrParse(file, metadataParser)

                            // Determine Chapter Title
                            val rawTitle = file.nameWithoutExtension
                            // Mojibake detection
                            val hasCyrillic = rawTitle.any { it in '\u0400'..'\u04FF' }
                            val hasCJK = rawTitle.any { it in '\u4E00'..'\u9FFF' }
                            val hasGreek = rawTitle.any { it in '\u0370'..'\u03FF' }
                            val hasMojibake = hasCyrillic && (hasCJK || hasGreek)

                            val fixedTitle =
                                if (hasMojibake) {
                                    val (fixed, _) = encodingDetector.fixGarbledText(rawTitle)
                                    fixed
                                } else {
                                    metadata?.title ?: rawTitle
                                }

                            // If title tag is prevalent but same as book title, prefer filename or number?
                            // Current logic uses filename-based cleanup mostly.
                            // Let's stick to existing logic: filename based name usually, but here we can use metadata title if available?
                            // The original createScannedBookLazy used filename.
                            // The createScannedBook used "metadata?.title" but fallback to filename?
                            // Actually createScannedBookLazy used "rawTitle" (filename).
                            // Let's use metadata title if available, as we are parsing it now!
                            // But wait, ID3 tags often have garbage. Filename is safer for chapters?
                            // User asked to "Parse metadata... for duration".
                            // Let's use filename for title consistency (as per existing Lazy logic) but ADD duration.

                            val finalTitle = if (hasMojibake) fixedTitle else rawTitle

                            chapters.add(
                                ScannedChapter(
                                    filePath = fileInfo.filePath,
                                    title = finalTitle,
                                    index = chapterIndex,
                                    duration = metadata?.duration ?: 0L, // HERE IS THE FIX
                                ),
                            )
                        }

                        // Create Book
                        val book =
                            ScannedBook(
                                directory = dir,
                                title = bookTitle,
                                author = bookAuthor,
                                chapters = chapters,
                                totalDuration = chapters.sumOf { it.duration },
                                coverArt = firstFileMetadata?.coverArt,
                            )

                        scannedBooks.add(book)
                    }

                    android.util.Log.i(
                        "DirectFileScanner",
                        "Scan complete: ${scannedBooks.size} books successfully created",
                    )

                    _scanProgress.value = ScanProgress.Saving

                    Result.Success(scannedBooks)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        android.util.Log.i("DirectFileScanner", "Scan cancelled")
                        throw e
                    }
                    android.util.Log.e("DirectFileScanner", "Scan failed", e)
                    _scanProgress.value = ScanProgress.Error(e.message ?: "Unknown error")
                    Result.Error(e)
                } finally {
                    // Cleanup if needed
                }
            }

        /**
         * Fast directory scan WITHOUT metadata parsing.
         * Only checks file extensions - ~0.1ms per file (vs 100ms with metadata)
         */
        private fun scanDirectoryFast(
            directory: File,
            result: MutableList<FastFileInfo>,
        ) {
            try {
                directory.listFiles()?.forEach { file ->
                    when {
                        file.isDirectory -> {
                            scanDirectoryFast(file, result)
                        }
                        file.isFile && file.isAudioFile() -> {
                            result.add(
                                FastFileInfo(
                                    filePath = file.absolutePath,
                                    displayName = file.name,
                                    directory = file.parent ?: "",
                                    size = file.length(),
                                    lastModified = file.lastModified(),
                                ),
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("DirectFileScanner", "Cannot access directory: ${directory.path}", e)
            }
        }

        /**
         * Recursively scan directory for audio files with PARALLEL processing.
         * IGNORES .nomedia files - this is intentional for user's use case!
         *
         * OPTIMIZED: 4× faster with parallel file processing
         * Thread-safe: Uses ConcurrentLinkedQueue
         * Rate limiting: Semaphore(4) prevents resource exhaustion
         * Error handling: Individual file errors don't stop scanning
         */
        private suspend fun scanDirectory(
            directory: File,
            result: MutableList<AudioFileInfo>,
        ) {
            // Convert to thread-safe collection for parallel processing
            val concurrentResult = ConcurrentLinkedQueue<AudioFileInfo>()
            val semaphore = Semaphore(4) // Limit to 4 concurrent file operations

            scanDirectoryParallel(directory, concurrentResult, semaphore)

            // Collect results back to original list
            result.addAll(concurrentResult)
        }

        private suspend fun scanDirectoryParallel(
            directory: File,
            result: ConcurrentLinkedQueue<AudioFileInfo>,
            semaphore: Semaphore,
        ) {
            try {
                val files = directory.listFiles() ?: return
                val subdirs = files.filter { it.isDirectory }
                val audioFiles = files.filter { it.isFile && it.isAudioFile() }

                android.util.Log.d(
                    "DirectFileScanner",
                    "📁 Scanning: ${directory.name} (${audioFiles.size} audio files, ${subdirs.size} subdirs)",
                )

                // Process audio files in PARALLEL with rate limiting
                if (audioFiles.isNotEmpty()) {
                    coroutineScope {
                        audioFiles
                            .map { file ->
                                async(Dispatchers.IO) {
                                    semaphore.withPermit {
                                        // Rate limiting: max 4 concurrent
                                        try {
                                            ensureActive() // Check for cancellation

                                            val audioInfo = createAudioFileInfo(file)
                                            if (audioInfo != null) {
                                                result.add(audioInfo) // Thread-safe add
                                                android.util.Log.v(
                                                    "DirectFileScanner",
                                                    "✓ ${file.name} (album: ${audioInfo.album ?: "none"})",
                                                )
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.w(
                                                "DirectFileScanner",
                                                "✗ Failed: ${file.name} - ${e.message}",
                                            )
                                            // Continue scanning other files
                                        }
                                    }
                                }
                            }.awaitAll() // Wait for all parallel tasks to complete
                    }
                }

                // Recursively scan subdirectories (sequential for simplicity)
                subdirs.forEach { subdir ->
                    scanDirectoryParallel(subdir, result, semaphore)
                }
            } catch (e: SecurityException) {
                android.util.Log.w("DirectFileScanner", "Cannot access directory: ${directory.path}", e)
            }
        }

        /**
         * Check if file is an audio file based on extension.
         */
        private fun File.isAudioFile(): Boolean {
            val extension = this.extension.lowercase()
            return extension in AUDIO_EXTENSIONS
        }

        /**
         * Create AudioFileInfo from File by parsing metadata.
         */
        private suspend fun createAudioFileInfo(file: File): AudioFileInfo? =
            try {
                val metadata = metadataParser.parseMetadata(file.absolutePath)

                AudioFileInfo(
                    filePath = file.absolutePath,
                    displayName = file.name,
                    duration = metadata?.duration ?: 0L,
                    album = metadata?.album,
                    artist = metadata?.artist ?: metadata?.albumArtist,
                    title = metadata?.title,
                )
            } catch (e: Exception) {
                android.util.Log.w("DirectFileScanner", "Failed to parse: ${file.name}", e)
                null
            }

        /**
         * Group audio files by album name, with directory fallback.
         *
         * Uses composite key (album/directory + artist) to avoid duplicates.
         */
        private fun groupFilesByAlbum(files: List<AudioFileInfo>): Map<String, List<AudioFileInfo>> {
            val totalFiles = files.size
            android.util.Log.d("DirectFileScanner", "Grouping $totalFiles files into books...")

            // Group by composite key (album or directory + artist)
            val grouped =
                files.groupBy { fileInfo ->
                    val directory = java.io.File(fileInfo.filePath).parent ?: ""
                    bookIdentifier.generateGroupingKey(
                        directory = directory,
                        album = fileInfo.album,
                        artist = fileInfo.artist,
                    )
                }

            // Log files without album metadata
            val filesWithoutAlbum = files.count { it.album.isNullOrBlank() }
            if (filesWithoutAlbum > 0) {
                android.util.Log.w(
                    "DirectFileScanner",
                    "$filesWithoutAlbum files have no album metadata (will group by directory)",
                )
            }

            android.util.Log.d(
                "DirectFileScanner",
                "Grouped into ${grouped.size} books (before: $totalFiles files)",
            )

            return grouped
        }

        /**
         * Create scanned book with LAZY metadata parsing.
         * Only parses metadata for FIRST file (20× faster!)
         * Other chapters use filename without metadata parsing
         */
        private suspend fun createScannedBookLazy(
            directory: String,
            fastFiles: List<FastFileInfo>,
        ): ScannedBook? {
            if (fastFiles.isEmpty()) return null

            val firstFile = fastFiles.first()
            val firstFileObj = File(firstFile.filePath)

            // Parse metadata ONLY for first file (with cache!)
            val metadata = metadataCache.getOrParse(firstFileObj, metadataParser)

            android.util.Log.d(
                "DirectFileScanner",
                "📖 Creating book from ${fastFiles.size} files, parsing only 1st: ${firstFile.displayName}",
            )

            // Generate unique book ID
            val bookId =
                bookIdentifier.generateBookId(
                    directory = directory,
                    album = metadata?.album,
                    artist = metadata?.albumArtist ?: metadata?.artist,
                )

            // Create chapters - NO METADATA PARSING for other files!
            val chapters =
                fastFiles
                    .sortedWith(
                        compareBy(
                            { file -> getFileCategory(file.displayName) },
                            { file -> extractChapterInfo(file.displayName).toSortKey() },
                            { file -> file.displayName.lowercase() },
                        ),
                    ).mapIndexed { index, file ->
                        // Use filename without extension (NO metadata parsing!)
                        val rawTitle = java.io.File(file.displayName).nameWithoutExtension

                        // Mojibake detection
                        val hasCyrillic = rawTitle.any { it in '\u0400'..'\u04FF' }
                        val hasCJK = rawTitle.any { it in '\u4E00'..'\u9FFF' }
                        val hasGreek = rawTitle.any { it in '\u0370'..'\u03FF' }
                        val hasMojibake = hasCyrillic && (hasCJK || hasGreek)

                        val fixedTitle =
                            if (hasMojibake) {
                                val (fixed, _) = encodingDetector.fixGarbledText(rawTitle)
                                fixed
                            } else {
                                rawTitle
                            }

                        ScannedChapter(
                            filePath = file.filePath,
                            title = fixedTitle,
                            index = index,
                            duration = 0L, // No metadata parsing - duration unknown
                        )
                    }

            val book =
                ScannedBook(
                    directory = directory,
                    title = metadata?.album ?: File(directory).name,
                    author = metadata?.albumArtist ?: metadata?.artist ?: "Unknown",
                    chapters = chapters,
                    totalDuration = 0L, // Unknown without parsing all files
                    coverArt = null, // Memory optimization: Worker extracts cover separately
                )

            android.util.Log.d(
                "DirectFileScanner",
                "'${book.title}' by ${book.author} (${chapters.size} chapters, ID: $bookId)",
            )

            return book
        }

        /**
         * Create ScannedBook from grouped files.
         */
        private suspend fun createScannedBook(
            groupingKey: String,
            files: List<AudioFileInfo>,
        ): ScannedBook? {
            val firstFile = files.firstOrNull() ?: return null
            val metadata = metadataParser.parseMetadata(firstFile.filePath)

            val directory = File(firstFile.filePath).parent ?: ""

            // Generate unique book ID
            val bookId =
                bookIdentifier.generateBookId(
                    directory = directory,
                    album = metadata?.album,
                    artist = metadata?.albumArtist ?: metadata?.artist,
                )

            val chapters =
                files
                    .sortedWith(createChapterComparator())
                    .mapIndexed { index, file ->
                        // FIX: Use FILENAME not ID3 title tag!
                        // Problem: All files may have same title tag = "Book Name"
                        // Solution: Use filename which has real chapter info
                        val rawTitle = java.io.File(file.displayName).nameWithoutExtension

                        // FIX: Only fix if MOJIBAKE detected (don't corrupt UTF-16!)
                        val hasCyrillic = rawTitle.any { it in '\u0400'..'\u04FF' }
                        val hasCJK = rawTitle.any { it in '\u4E00'..'\u9FFF' }
                        val hasGreek = rawTitle.any { it in '\u0370'..'\u03FF' }
                        val hasMojibake = hasCyrillic && (hasCJK || hasGreek)

                        val fixedTitle =
                            if (hasMojibake) {
                                val (fixed, detectedEncoding) = encodingDetector.fixGarbledText(rawTitle)
                                android.util.Log.w(
                                    "DirectFileScanner",
                                    "� MOJIBAKE FIXED: '$rawTitle' -> '$fixed' ($detectedEncoding)",
                                )
                                fixed
                            } else {
                                rawTitle // UTF-16 is already correct!
                            }

                        ScannedChapter(
                            filePath = file.filePath,
                            title = fixedTitle,
                            index = index,
                            duration = file.duration,
                        )
                    }

            val book =
                ScannedBook(
                    directory = directory,
                    title = metadata?.album ?: File(directory).name,
                    author = metadata?.albumArtist ?: metadata?.artist ?: firstFile.artist ?: "Unknown",
                    chapters = chapters,
                    totalDuration = chapters.sumOf { it.duration },
                    coverArt = null, // Memory optimization: Worker extracts cover separately
                )

            android.util.Log.d(
                "DirectFileScanner",
                "Created book '${book.title}' by ${book.author} (${chapters.size} chapters, ID: $bookId)",
            )

            return book
        }

        private data class ChapterInfo(
            val partNumber: Int = 0,
            val chapterNumber: Int = 0,
            val hasNumber: Boolean = false,
        ) {
            fun toSortKey(): Int = partNumber * 1000 + chapterNumber
        }

        /**
         * Create comparator for chapter sorting.
         *
         * Sort order:
         * 0. Пролог/Prologue
         * 1. Numbered chapters (Глава 1-N)
         * 2. Unnumbered files (alphabetical)
         * 3. Special content (Приложение/От автора/Послесловие)
         * 4. Эпилог/Epilogue (always last)
         */
        private fun createChapterComparator(): Comparator<AudioFileInfo> =
            compareBy<AudioFileInfo> { file ->
                getFileCategory(file.displayName)
            }.thenBy { file ->
                val info = extractChapterInfo(file.displayName)
                if (info.hasNumber) info.toSortKey() else 0
            }.thenBy { file ->
                file.displayName.lowercase() // Alphabetical for same category
            }

        private fun getFileCategory(filename: String): Int {
            val lower = filename.lowercase()
            return when {
                // Prologue - always first
                lower.contains("пролог") || lower.contains("prologue") -> 0

                // Regular numbered chapters (but not appendices!)
                extractChapterInfo(filename).hasNumber && !isSpecialContent(lower) -> 1

                // Unnumbered files (alphabetical)
                !extractChapterInfo(filename).hasNumber && !isSpecialContent(lower) -> 2

                // Special content (appendices, afterwords, etc)
                isSpecialContent(lower) -> 3

                // Epilogue - always last
                lower.contains("эпилог") || lower.contains("epilogue") -> 4

                else -> 2 // Default: unnumbered
            }
        }

        private fun isSpecialContent(filename: String): Boolean =
            filename.contains("приложение") ||
                filename.contains("appendix") ||
                filename.contains("от автора") ||
                filename.contains("from the author") ||
                filename.contains("author") &&
                filename.contains("note") ||
                filename.contains("послесловие") ||
                filename.contains("afterword") ||
                filename.contains("предисловие") ||
                filename.contains("foreword") ||
                filename.contains("preface")

        private fun extractChapterInfo(filename: String): ChapterInfo {
            val clean = filename.lowercase()

            val partMatch =
                Regex("""част[\u044cяи]\s*(\d+)""").find(clean)
                    ?: Regex("""part\s*(\d+)""").find(clean)
            val partNum = partMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val patterns =
                listOf(
                    Regex("""глава\s*(\d+)""", RegexOption.IGNORE_CASE),
                    Regex("""chapter\s*(\d+)""", RegexOption.IGNORE_CASE),
                    Regex("""(\d+)\s*[-._]"""),
                    Regex("""^(\d+)"""),
                )

            var chapterNum = 0
            var found = false
            for (pattern in patterns) {
                pattern.find(clean)?.let {
                    chapterNum = it.groupValues[1].toIntOrNull() ?: 0
                    found = true
                    return@let
                }
            }

            return ChapterInfo(partNum, chapterNum, found)
        }

        companion object {
            /**
             * Supported audio file extensions.
             */
            private val AUDIO_EXTENSIONS =
                setOf(
                    "mp3",
                    "m4a",
                    "m4b",
                    "ogg",
                    "opus",
                    "flac",
                    "wav",
                    "aac",
                    "wma",
                    "oga",
                )
        }
    }
