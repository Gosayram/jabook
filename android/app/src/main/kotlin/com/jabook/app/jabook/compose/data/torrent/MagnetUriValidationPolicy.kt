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

package com.jabook.app.jabook.compose.data.torrent

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Strict validation policy for magnet links used by download/streaming flows.
 *
 * Accepts:
 * - `magnet:?xt=urn:btih:<40-hex or 32-base32>`
 * - raw 40-hex info-hash (for internal compatibility paths)
 */
public object MagnetUriValidationPolicy {
    private val hexRegex = "^[a-fA-F0-9]{40}$".toRegex()
    private val base32Regex = "^[a-zA-Z2-7]{32}$".toRegex()

    public fun isValidMagnetUri(rawUri: String): Boolean = extractInfoHash(rawUri) != null

    public fun isValidMagnetUri(uri: Uri): Boolean = isValidMagnetUri(uri.toString())

    public fun extractInfoHash(rawUri: String): String? {
        val trimmed = rawUri.trim()
        if (trimmed.isEmpty()) return null

        if (hexRegex.matches(trimmed)) {
            return trimmed.lowercase()
        }

        if (!trimmed.startsWith("magnet:", ignoreCase = true)) {
            return null
        }

        val query = trimmed.substringAfter('?', "")
        if (query.isBlank()) return null
        val xtEncoded =
            query
                .split("&")
                .firstOrNull { parameter ->
                    parameter.substringBefore("=", "").equals("xt", ignoreCase = true)
                }?.substringAfter("=", "")
                ?.trim()
                ?: return null
        val xt = URLDecoder.decode(xtEncoded, StandardCharsets.UTF_8.name())
        if (!xt.startsWith("urn:btih:", ignoreCase = true)) return null
        val infoHash = xt.substringAfter("urn:btih:", "").trim()
        return when {
            hexRegex.matches(infoHash) -> infoHash.lowercase()
            base32Regex.matches(infoHash) -> base32ToHex(infoHash)
            else -> null
        }
    }

    private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    /**
     * RFC4648 base32 (32 chars) → 20-byte lowercase hex, matching the canonical
     * hex form used everywhere else as info-hash key. Returns null on decode failure.
     */
    private fun base32ToHex(input: String): String? {
        var buffer = 0L
        var bits = 0
        val bytes = ByteArray(20)
        var index = 0
        for (character in input.lowercase()) {
            val value = BASE32_ALPHABET.indexOf(character)
            if (value < 0) return null
            buffer = (buffer shl 5) or value.toLong()
            bits += 5
            if (bits >= 8) {
                bytes[index++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        if (index != bytes.size) return null
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
