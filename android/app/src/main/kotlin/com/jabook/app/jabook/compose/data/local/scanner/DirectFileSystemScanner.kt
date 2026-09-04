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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
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
public class DirectFileSystemScanner
    @Inject
    constructor(
        private val metadataParser: AudioMetadataParser,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val bookIdentifier: BookIdentifier,
        private val encodingDetector: com.jabook.app.jabook.compose.data.local.parser.EncodingDetector,
        private val metadataCache: com.jabook.app.jabook.compose.data.local.parser.MetadataCache,
        private val loggerFactory: LoggerFactory,
    ) : LocalBookScanner {
        private val logger = loggerFactory.get("DirectFileSystemScanner")

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

        /**
         * Directories whose book parse failed during the most recent scan. Their files are
         * treated as always-changed so a transient failure isn't hidden forever by the
         * bumped last_scan_timestamp.
         */
        private var lastScanFailedDirs: Set<String> = emptySet()

        override suspend fun scanAudiobooks(): Result<List<ScannedBook>, com.jabook.app.jabook.compose.domain.model.AppError> =
            withContext(Dispatchers.IO) {
                try {
                    val customPaths = scanPathDao.getAllPathsList().map { it.path }

                    if (customPaths.isEmpty()) {
                        return@withContext Result.Success(emptyList())
                    }

                    _scanProgress.value = ScanProgress.Discovery(0)

                    // Capture BEFORE discovery so files modified mid-scan are not skipped next run
                    val scanStartTime = System.currentTimeMillis()

                    // PHASE 1: FAST SCAN - No metadata parsing
                    logger.i { "Phase 1: Fast scan (no metadata)" }
                    val fastFiles = mutableListOf<FastFileInfo>()
                    for (path in customPaths) {
                        ensureActive() // Check for cancellation
                        val directory = File(path)
                        if (directory.exists() && directory.isDirectory) {
                            scanDirectoryFast(directory, fastFiles)
                            _scanProgress.value = ScanProgress.Discovery(fastFiles.size)
                        }
                    }

                    // Dedupe files discovered via overlapping scan paths (parent + subdir both registered)
                    val distinctFiles = fastFiles.distinctBy { it.filePath }

                    val totalFiles = distinctFiles.size
                    logger.i { "Found $totalFiles audio files (fast scan)" }

                    // PHASE 1.5: INCREMENTAL SCAN FILTER
                    // Build a map of path -> lastScanTimestamp for incremental filtering
                    val pathEntities = scanPathDao.getAllPathsList()
                    val pathTimestampMap = pathEntities.associate { it.path to it.lastScanTimestamp }

                    // Group fast files by their root scan path for per-path filtering
                    val filteredFiles = mutableListOf<FastFileInfo>()
                    for (path in customPaths) {
                        val lastTimestamp = pathTimestampMap[path]
                        val filesForPath = distinctFiles.filter { it.filePath.startsWith(path) }
                        val scanInfos =
                            filesForPath.map {
                                IncrementalScanPolicy.FileScanInfo(
                                    filePath = it.filePath,
                                    displayName = it.displayName,
                                    directory = it.directory,
                                    size = it.size,
                                    lastModified = it.lastModified,
                                )
                            }
                        val filterResult = IncrementalScanPolicy.filterChangedFiles(scanInfos, lastTimestamp)
                        if (filterResult.isFullScan) {
                            filteredFiles.addAll(filesForPath)
                        } else {
                            // Books live per-directory: rebuild each book from ALL its files when
                            // ANY file changed, otherwise upsert would wipe chapters of unchanged files.
                            val changedDirs = filterResult.filesToScan.mapTo(mutableSetOf()) { it.directory }
                            changedDirs.addAll(lastScanFailedDirs)
                            filteredFiles.addAll(filesForPath.filter { it.directory in changedDirs })
                        }
                        if (filterResult.skippedCount > 0) {
                            logger.i {
                                "Incremental: skipped ${filterResult.skippedCount} unchanged files in $path"
                            }
                        }
                    }

                    // Overlapping scan paths can add the same file twice — dedupe before grouping
                    val effectiveFiles = filteredFiles.distinctBy { it.filePath }
                    logger.i {
                        "Incremental scan: ${effectiveFiles.size}/$totalFiles files need processing"
                    }

                    // PHASE 2: GROUP by directory
                    val groupedByDir = effectiveFiles.groupBy { it.directory }
                    logger.i {
                        "Grouped into ${groupedByDir.size} books (by directory)"
                    }

                    // PHASE 3: Parse metadata for ALL files (Fix for missing duration)
                    logger.i { "Phase 3: Parsing metadata (Full Scan)" }

                    val scannedBooks = mutableListOf<ScannedBook>()
                    val failedDirs = mutableSetOf<String>()
                    var processedFilesCount = 0

                    groupedByDir.entries.forEachIndexed { index, (dir, files) ->
                        ensureActive() // Check for cancellation

                        val bookName = File(dir).name

                        // One malformed book must not abort the whole scan
                        try {
                            val unsortedFiles = files

                            // Detect Book Metadata from FIRST file (as fallback if others fail, or for Album name)
                            // We still use the first file for Book-level metadata (Author/Cover) usually
                            val firstFile = unsortedFiles.first()
                            val firstFileMetadata = metadataCache.getOrParse(File(firstFile.filePath), metadataParser)
                            val structureType =
                                BookStructureHeuristics.classify(
                                    fileNames = unsortedFiles.map { it.displayName },
                                    hasNestedDirectories =
                                        File(dir).listFiles()?.any { it.isDirectory } == true,
                                    singleFileDurationMs = firstFileMetadata?.duration,
                                )
                            logger.d { "Book structure detected for '$bookName': $structureType" }

                            val bookTitle = firstFileMetadata?.album ?: File(dir).name
                            val bookAuthor = firstFileMetadata?.albumArtist ?: firstFileMetadata?.artist ?: "Unknown"

                            data class ParsedChapter(
                                val filePath: String,
                                val displayName: String,
                                val title: String,
                                val duration: Long,
                                val trackNumber: Int?,
                            )
                            val parsedChapters = mutableListOf<ParsedChapter>()

                            // Parse EVERY file to get duration
                            for (fileInfo in unsortedFiles) {
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

                                val finalTitle = if (hasMojibake) fixedTitle else rawTitle

                                parsedChapters.add(
                                    ParsedChapter(
                                        filePath = fileInfo.filePath,
                                        displayName = fileInfo.displayName,
                                        title = finalTitle,
                                        duration = metadata?.duration ?: 0L,
                                        trackNumber = metadata?.trackNumber,
                                    ),
                                )
                            }

                            val chapterOrderComparator = ChapterOrderPolicy.comparator()
                            val chapters =
                                parsedChapters
                                    .sortedWith { left, right ->
                                        chapterOrderComparator.compare(
                                            ChapterOrderCandidate(
                                                displayName = left.displayName,
                                                trackNumber = left.trackNumber,
                                            ),
                                            ChapterOrderCandidate(
                                                displayName = right.displayName,
                                                trackNumber = right.trackNumber,
                                            ),
                                        )
                                    }.mapIndexed { chapterIndex, chapter ->
                                        ScannedChapter(
                                            filePath = chapter.filePath,
                                            title = chapter.title,
                                            index = chapterIndex,
                                            duration = chapter.duration,
                                        )
                                    }

                            val finalChapters =
                                expandEmbeddedChapters(chapters, firstFileMetadata?.duration) ?: chapters

                            // Create Book
                            val book =
                                ScannedBook(
                                    directory = dir,
                                    title = bookTitle,
                                    author = bookAuthor,
                                    chapters = finalChapters,
                                    totalDuration = finalChapters.sumOf { it.duration },
                                    coverArt = firstFileMetadata?.coverArt,
                                )

                            scannedBooks.add(book)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedDirs.add(dir)
                            logger.e({ "Failed to process book '$bookName', skipping" }, e)
                        }
                    }

                    // Refresh for the next incremental scan; dirs that now succeed drop out.
                    lastScanFailedDirs = failedDirs

                    logger.i { "Scan complete: ${scannedBooks.size} books successfully created" }

                    _scanProgress.value = ScanProgress.Saving

                    // Update last_scan_timestamp for all scan paths after successful scan.
                    // Use the pre-scan start time so files modified mid-scan are not skipped next run.
                    for (path in customPaths) {
                        scanPathDao.updateLastScanTimestamp(path, scanStartTime)
                    }
                    logger.i { "Updated scan timestamps for ${customPaths.size} paths" }

                    Result.Success(scannedBooks)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        logger.i { "Scan cancelled" }
                        throw e
                    }
                    logger.e({ "Scan failed" }, e)
                    _scanProgress.value = ScanProgress.Error(e.message ?: "Unknown error")
                    Result.Error(
                        com.jabook.app.jabook.compose.domain.model.AppError.DataError
                            .Generic(e.message ?: "Scan failed", e),
                    )
                } finally {
                    // Cleanup if needed
                }
            }

        /**
         * Fast directory scan WITHOUT metadata parsing.
         * Only checks file extensions - ~0.1ms per file (vs 100ms with metadata)
         *
         * Depth-capped with a visited set of canonical paths to survive FUSE/symlink
         * loops without stack overflow. Skips hidden directories (".Trash" etc).
         */
        private fun scanDirectoryFast(
            directory: File,
            result: MutableList<FastFileInfo>,
            depth: Int = 0,
            visited: MutableSet<String> = mutableSetOf(),
        ) {
            if (depth > MAX_SCAN_DEPTH) return
            val canonicalPath =
                try {
                    directory.canonicalPath
                } catch (e: Exception) {
                    directory.absolutePath
                }
            if (!visited.add(canonicalPath)) return

            try {
                directory.listFiles()?.forEach { file ->
                    when {
                        file.isDirectory -> {
                            if (!file.name.startsWith(".")) {
                                scanDirectoryFast(file, result, depth + 1, visited)
                            }
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
                logger.e({ "Cannot access directory: ${directory.path}" }, e)
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
         * For a single-file m4b/m4a, tries to parse embedded Nero chapter
         * markers and expand into multiple [ScannedChapter] entries.
         *
         * @return expanded chapters or null when parsing is not applicable
         */
        private fun expandEmbeddedChapters(
            chapters: List<ScannedChapter>,
            fileDurationMs: Long?,
        ): List<ScannedChapter>? {
            if (chapters.size != 1) return null
            val only = chapters.first()
            val ext = only.filePath.substringAfterLast('.').lowercase()
            if (ext !in EMBEDDED_CHAPTER_EXTENSIONS) return null

            val embedded = M4bChapterParser.parseM4bChapters(only.filePath) ?: return null
            if (embedded.size < 2) return null
            val durationMs = fileDurationMs?.takeIf { it > embedded.last().startMs } ?: return null

            return embedded.mapIndexed { index, ch ->
                val endMs =
                    if (index < embedded.size - 1) {
                        embedded[index + 1].startMs
                    } else {
                        durationMs
                    }
                ScannedChapter(
                    filePath = only.filePath,
                    title = ch.title,
                    index = index,
                    duration = (endMs - ch.startMs).coerceAtLeast(0),
                    startMs = ch.startMs,
                    endMs = endMs,
                )
            }
        }

        public companion object {
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

            /** Extensions that may contain embedded Nero chapter atoms. */
            private val EMBEDDED_CHAPTER_EXTENSIONS = setOf("m4b", "m4a")

            /** Max recursion depth for the fast scan; guards against pathological trees. */
            private const val MAX_SCAN_DEPTH = 20
        }
    }
