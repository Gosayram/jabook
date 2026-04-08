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

/**
 * Policy for safe handling of artwork payloads in Media3 metadata pipeline.
 *
 * Oversized artwork data can cause OOM in SysUI/notification decode paths and
 * increase IPC payload size. This policy enforces a maximum size for raw
 * artwork byte arrays and documents the preference for `artworkUri` over
 * `artworkData` where possible.
 *
 * Recommended by Media3 1.10.0 best practices:
 * - Prefer `artworkUri` (lighter IPC, on-demand decode)
 * - Guard `artworkData` with a size limit before decode
 * - Reject oversized payloads early to avoid native crashes
 */
public object ArtworkPayloadPolicy {
    /**
     * Maximum allowed artwork byte-array size (2 MB).
     * Covers most cover-art use cases while protecting against oversized
     * embedded images that could OOM the SysUI or notification process.
     */
    public const val MAX_ARTWORK_DATA_BYTES: Int = 2 * 1024 * 1024

    /**
     * Returns `true` when [data] is within the allowed size range
     * and can safely be passed to bitmap decode.
     */
    public fun isArtworkDataAllowed(data: ByteArray?): Boolean {
        if (data == null) return false
        return data.size <= MAX_ARTWORK_DATA_BYTES
    }

    /**
     * Sanitizes artwork data: returns the original array if within limits,
     * or `null` if the payload exceeds the safe threshold.
     *
     * Use this at the boundary where artwork bytes enter the decode pipeline.
     */
    public fun sanitizeArtworkData(data: ByteArray?): ByteArray? = if (isArtworkDataAllowed(data)) data else null
}
