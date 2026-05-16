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

package com.jabook.app.jabook.audio

import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Proactive media source validator that checks file integrity **before**
 * loading into ExoPlayer.
 *
 * [TrackAvailabilityChecker] only verifies that a file exists and is
 * readable, but a corrupted or truncated file will still cause a
 * `PlaybackException` at runtime. This validator adds:
 * - Minimum file size check (files < 1 KB are almost certainly corrupt)
 * - Magic-bytes validation for known audio formats
 *
 * P-11: Prevents `SOURCE_ERROR` by filtering bad files early.
 */
public object MediaSourceValidator {
    /**
     * Result of validating a list of file paths.
     *
     * @property validPaths Files that passed all checks.
     * @property invalidPaths Files that don't exist or aren't readable.
     * @property corruptedPaths Files that exist but fail integrity checks.
     */
    public data class ValidationResult(
        public val validPaths: List<String>,
        public val invalidPaths: List<String>,
        public val corruptedPaths: List<String>,
    )

    /** Minimum file size in bytes — files below this are considered corrupt. */
    private const val MIN_FILE_SIZE_BYTES: Long = 1024L
    private const val TAG = "MediaSourceValidator"

    /**
     * Validates a list of file paths asynchronously on [Dispatchers.IO].
     *
     * @param filePaths Paths to validate.
     * @return [ValidationResult] partitioned into valid / invalid / corrupted.
     */
    public suspend fun validate(filePaths: List<String>): ValidationResult =
        withContext(Dispatchers.IO) {
            val valid = mutableListOf<String>()
            val invalid = mutableListOf<String>()
            val corrupted = mutableListOf<String>()

            for (path in filePaths) {
                val file = File(path)
                when {
                    !file.exists() || !file.isFile || !file.canRead() -> {
                        LogUtils.w(TAG, "Invalid file: $path")
                        invalid.add(path)
                    }
                    file.length() < MIN_FILE_SIZE_BYTES -> {
                        LogUtils.w(TAG, "File too small (${file.length()}B): $path")
                        corrupted.add(path)
                    }
                    !isValidAudioFile(file) -> {
                        LogUtils.w(TAG, "Unknown audio header: $path")
                        corrupted.add(path)
                    }
                    else -> {
                        valid.add(path)
                    }
                }
            }

            ValidationResult(valid, invalid, corrupted)
        }

    /**
     * Validates a single file synchronously.
     *
     * @param path File path to validate.
     * @return `true` if the file is a valid, readable audio file.
     */
    public fun isValid(path: String): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canRead()) return false
        if (file.length() < MIN_FILE_SIZE_BYTES) return false
        return isValidAudioFile(file)
    }

    /**
     * Checks magic bytes of a file against known audio format signatures.
     *
     * Supported formats:
     * - MP3: `0xFF 0xFB` or `0xFF 0xF3` or `0xFF 0xF2` or ID3v2 `0x49 0x44 0x33`
     * - M4A/M4B/MP4: `ftyp` at offset 4 (`0x66 0x74 0x79 0x70`)
     * - OGG: `OggS` (`0x4F 0x67 0x67 0x53`)
     * - FLAC: `fLaC` (`0x66 0x4C 0x61 0x43`)
     * - WAV: `RIFF` (`0x52 0x49 0x46 0x46`)
     * - WMA/ASF: `0x30 0x26 0xB2 0x75`
     * - Opus (in OGG): detected via OGG container
     */
    private fun isValidAudioFile(file: File): Boolean =
        try {
            val header = ByteArray(12)
            file.inputStream().use { it.read(header) }
            isKnownAudioHeader(header)
        } catch (e: IOException) {
            LogUtils.e(TAG, "Failed to read header: ${file.path}", e)
            false
        }

    /**
     * Checks whether the given byte header matches any known audio format.
     *
     * @param header At least 12 bytes read from the beginning of the file.
     * @return `true` if the header is recognised as a valid audio format.
     */
    public fun isKnownAudioHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false

        // MP3 sync word: 0xFF + upper 6 bits of next byte = 111111b
        if (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) return true

        // MP3 with ID3v2 tag: "ID3"
        if (header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        ) {
            return true
        }

        // MP4/M4A/M4B: bytes 4-7 = "ftyp"
        if (header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            return true
        }

        // OGG: "OggS"
        if (header[0] == 'O'.code.toByte() &&
            header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() &&
            header[3] == 'S'.code.toByte()
        ) {
            return true
        }

        // FLAC: "fLaC"
        if (header[0] == 'f'.code.toByte() &&
            header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() &&
            header[3] == 'C'.code.toByte()
        ) {
            return true
        }

        // WAV: "RIFF"
        if (header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) {
            return true
        }

        // WMA/ASF
        if (header[0] == 0x30.toByte() &&
            header[1] == 0x26.toByte() &&
            header[2] == 0xB2.toByte() &&
            header[3] == 0x75.toByte()
        ) {
            return true
        }

        return false
    }
}
