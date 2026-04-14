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

/**
 * Policy for validating audio file MIME types during import/scan.
 *
 * Validates files not only by extension but also by magic bytes (file signatures),
 * providing defense-in-depth against misnamed or malicious files being imported
 * into the audio pipeline.
 *
 * BP-14.4 reference: MIME type validation при импорте — валидировать через
 * magic bytes (не только расширение). Reject с explicit error вместо silent failure.
 */
public object MimeTypeValidationPolicy {
    /**
     * Result of a MIME type validation.
     *
     * @property isValid Whether the file passed validation.
     * @property detectedMimeType The MIME type detected from magic bytes (if available).
     * @property extensionMimeType The MIME type inferred from file extension.
     * @property rejectionReason Why the file was rejected (if [isValid] is false).
     */
    public data class ValidationResult(
        public val isValid: Boolean,
        public val detectedMimeType: String?,
        public val extensionMimeType: String?,
        public val rejectionReason: String?,
    )

    /**
     * Known audio MIME types supported by JaBook.
     */
    public val SUPPORTED_AUDIO_MIME_TYPES: Set<String> =
        setOf(
            "audio/mpeg", // MP3
            "audio/mp3",
            "audio/mp4", // M4A, M4B
            "audio/x-m4a",
            "audio/x-m4b",
            "audio/aac",
            "audio/x-aac",
            "audio/ogg", // OGG, Opus
            "audio/opus",
            "audio/flac", // FLAC
            "audio/x-flac",
            "audio/wav", // WAV
            "audio/x-wav",
            "audio/wave",
            "audio/webm", // WebM audio
            "audio/x-matroska", // MKV audio
            "audio/ogg; codecs=opus", // Opus in OGG container
        )

    /**
     * Supported audio file extensions.
     */
    public val SUPPORTED_EXTENSIONS: Set<String> =
        setOf(
            "mp3",
            "m4a",
            "m4b",
            "aac",
            "ogg",
            "opus",
            "flac",
            "wav",
            "wma",
            "webm",
            "mkv",
        )

    /**
     * Magic byte signatures for audio formats.
     * Maps a human-readable format name to its file signature bytes and offset.
     */
    private val MAGIC_SIGNATURES: List<MagicSignature> =
        listOf(
            // MP3: ID3 tag header or sync word 0xFF 0xFB / 0xFF 0xF3 / 0xFF 0xF2
            MagicSignature("mp3", byteRange(0xFF, 0xFB), 0),
            MagicSignature("mp3", byteRange(0xFF, 0xF3), 0),
            MagicSignature("mp3", byteRange(0xFF, 0xF2), 0),
            MagicSignature("mp3", byteArrayOf(0x49, 0x44, 0x33), 0), // "ID3"
            // MP4/M4A/M4B: ftyp box
            MagicSignature("mp4", byteRange(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70), 4), // ....ftyp at offset 4
            MagicSignature("mp4", byteRange(0x66, 0x74, 0x79, 0x70), 4), // "ftyp" at offset 4
            // OGG: "OggS"
            MagicSignature("ogg", byteArrayOf(0x4F, 0x67, 0x67, 0x53), 0), // "OggS"
            // FLAC: "fLaC"
            MagicSignature("flac", byteArrayOf(0x66, 0x4C, 0x61, 0x43), 0), // "fLaC"
            // WAV: "RIFF" + .... + "WAVE"
            MagicSignature("wav", byteArrayOf(0x52, 0x49, 0x46, 0x46), 0), // "RIFF"
            // WebM: EBML header
            MagicSignature("webm", byteRange(0x1A, 0x45, 0xDF, 0xA3), 0),
        )

    /**
     * Maps detected format name to canonical MIME type.
     */
    private val FORMAT_TO_MIME: Map<String, String> =
        mapOf(
            "mp3" to "audio/mpeg",
            "mp4" to "audio/mp4",
            "ogg" to "audio/ogg",
            "flac" to "audio/flac",
            "wav" to "audio/wav",
            "webm" to "audio/webm",
        )

    /**
     * Maps file extension to canonical MIME type.
     */
    private val EXTENSION_TO_MIME: Map<String, String> =
        mapOf(
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "m4b" to "audio/mp4",
            "aac" to "audio/aac",
            "ogg" to "audio/ogg",
            "opus" to "audio/ogg",
            "flac" to "audio/flac",
            "wav" to "audio/wav",
            "wma" to "audio/x-ms-wma",
            "webm" to "audio/webm",
            "mkv" to "audio/x-matroska",
        )

    /**
     * Validates a file by checking both its extension and magic bytes.
     *
     * @param fileName The file name (with extension).
     * @param headerBytes The first N bytes of the file (at least 16 recommended).
     * @return [ValidationResult] indicating whether the file is a valid audio file.
     */
    public fun validate(
        fileName: String,
        headerBytes: ByteArray,
    ): ValidationResult {
        val extension = extractExtension(fileName)
        val extensionMime = extension?.let { EXTENSION_TO_MIME[it.lowercase()] }

        if (extension != null && extension.lowercase() !in SUPPORTED_EXTENSIONS) {
            return ValidationResult(
                isValid = false,
                detectedMimeType = null,
                extensionMimeType = extensionMime,
                rejectionReason = "Unsupported file extension: .$extension",
            )
        }

        val detectedFormat = detectFormat(headerBytes)
        val detectedMime = detectedFormat?.let { FORMAT_TO_MIME[it] }

        if (detectedMime == null && headerBytes.isNotEmpty()) {
            // Magic bytes don't match any known audio format
            return ValidationResult(
                isValid = false,
                detectedMimeType = null,
                extensionMimeType = extensionMime,
                rejectionReason = "File magic bytes do not match any supported audio format",
            )
        }

        // Cross-validate extension vs magic bytes if both are available
        if (extensionMime != null && detectedMime != null) {
            if (!isMimeTypeCompatible(extensionMime, detectedMime)) {
                return ValidationResult(
                    isValid = false,
                    detectedMimeType = detectedMime,
                    extensionMimeType = extensionMime,
                    rejectionReason = "Extension suggests '$extensionMime' but content is '$detectedMime'",
                )
            }
        }

        return ValidationResult(
            isValid = true,
            detectedMimeType = detectedMime,
            extensionMimeType = extensionMime,
            rejectionReason = null,
        )
    }

    /**
     * Checks if a file extension is supported.
     */
    public fun isExtensionSupported(fileName: String): Boolean {
        val ext = extractExtension(fileName) ?: return false
        return ext.lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Gets the MIME type for a file extension.
     */
    public fun getMimeTypeForExtension(extension: String): String? = EXTENSION_TO_MIME[extension.lowercase()]

    /**
     * Detects the audio format from file header bytes.
     * Returns the format name (e.g., "mp3", "flac") or null if not recognized.
     */
    public fun detectFormat(headerBytes: ByteArray): String? {
        for (signature in MAGIC_SIGNATURES) {
            if (headerBytes.size < signature.offset + signature.bytes.size) continue

            val slice =
                headerBytes.copyOfRange(
                    signature.offset,
                    signature.offset + signature.bytes.size,
                )
            if (slice.contentEquals(signature.bytes)) {
                return signature.formatName
            }
        }
        return null
    }

    /**
     * Checks if two MIME types are compatible (same format family).
     */
    private fun isMimeTypeCompatible(
        extensionMime: String,
        detectedMime: String,
    ): Boolean {
        if (extensionMime == detectedMime) return true

        // Normalize and check base type compatibility
        val extBase =
            extensionMime
                .substringAfter("audio/")
                .substringBefore(";")
                .removePrefix("x-")
        val detBase =
            detectedMime
                .substringAfter("audio/")
                .substringBefore(";")
                .removePrefix("x-")

        // M4A/M4B are both MP4 containers
        val mp4Aliases = setOf("mp4", "m4a", "m4b", "aac")
        if (extBase in mp4Aliases && detBase in mp4Aliases) return true

        // OGG family (OGG, Opus)
        val oggAliases = setOf("ogg", "opus")
        if (extBase in oggAliases && detBase in oggAliases) return true

        return extBase == detBase
    }

    private fun extractExtension(fileName: String): String? {
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot < 0 || lastDot == fileName.length - 1) return null
        return fileName.substring(lastDot + 1)
    }

    private fun byteRange(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()

    private data class MagicSignature(
        val formatName: String,
        val bytes: ByteArray,
        val offset: Int,
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is MagicSignature && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}
