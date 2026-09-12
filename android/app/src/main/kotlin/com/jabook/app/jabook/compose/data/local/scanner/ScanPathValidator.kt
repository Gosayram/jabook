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

import java.io.File

/**
 * Why a persisted scan path cannot be scanned.
 */
public enum class ScanPathStatus {
    VALID,

    /** Legacy row holding a raw SAF URI — the File-based scanner can never read it. */
    UNSUPPORTED_URI,

    /** Path does not exist (deleted, or volume temporarily unmounted). */
    MISSING,

    /** Path exists but is a file, not a folder. */
    NOT_DIRECTORY,
}

/**
 * Shared scan-path hygiene: normalization, validity classification, and
 * multi-disc folder detection. Used by [HybridBookScanner] (cleanup +
 * user-facing skip count) and [DirectFileSystemScanner] (self-healing scan).
 */
public object ScanPathValidator {
    /**
     * Normalizes a persisted scan path: trims whitespace and trailing slashes
     * so " /storage/Books/ ", "/storage/Books//" and "/storage/Books" are one path.
     */
    public fun normalize(path: String): String =
        path
            .trim()
            .let { if (it.length > 1) it.trimEnd('/') else it }

    /**
     * Classifies a (normalized) scan path without mutating anything.
     */
    public fun classify(path: String): ScanPathStatus =
        when {
            path.startsWith("content://") -> ScanPathStatus.UNSUPPORTED_URI
            !File(path).exists() -> ScanPathStatus.MISSING
            !File(path).isDirectory -> ScanPathStatus.NOT_DIRECTORY
            else -> ScanPathStatus.VALID
        }

    /**
     * True when a directory name looks like a disc/part folder ("CD1", "Disc 2",
     * "Part_3") — such folders are merged into their parent book instead of
     * becoming separate books.
     */
    public fun isDiscDirectory(directoryName: String): Boolean = DISC_DIR_REGEX.matches(directoryName.trim())

    // ponytail: word+digit disc names only; extend if real-world layouts need more.
    private val DISC_DIR_REGEX = Regex("""(?i)^(cd|disc|dvd|part|pt|side)[\s._-]*\d+.*""")
}
