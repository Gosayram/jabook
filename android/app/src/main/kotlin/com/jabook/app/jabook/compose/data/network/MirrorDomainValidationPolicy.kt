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

package com.jabook.app.jabook.compose.data.network

/**
 * Policy for validating custom mirror domains before they are persisted.
 *
 * Ensures that user-supplied mirror input is sanitized to a bare domain
 * and rejects obviously invalid or dangerous values (local addresses,
 * IP literals, missing TLD, etc.).
 *
 * This policy does **not** perform network probes; it only validates
 * the syntactic form of the domain.
 */
public object MirrorDomainValidationPolicy {
    /**
     * Result of domain validation.
     *
     * @property sanitizedDomain The cleaned-up domain if validation succeeded, null otherwise.
     * @property isWarning Whether the domain is usable but looks suspicious (e.g., not a known rutracker domain).
     * @property rejectionReason Human-readable reason if validation failed.
     */
    public data class ValidationResult(
        public val sanitizedDomain: String?,
        public val isWarning: Boolean,
        public val rejectionReason: String?,
    ) {
        public val isValid: Boolean get() = sanitizedDomain != null
    }

    /**
     * Known RuTracker keywords that a legitimate mirror domain usually contains.
     *
     * Domains not matching any of these trigger a [isWarning] flag so the UI
     * can ask for explicit user confirmation.
     */
    public val KNOWN_RUTRACKER_KEYWORDS: List<String> =
        listOf("rutracker")

    /**
     * Validates and sanitizes a raw user-supplied mirror input.
     *
     * Processing steps:
     * 1. Trim whitespace
     * 2. Strip `http://` or `https://` protocol prefix
     * 3. Strip trailing path, query, fragment, port
     * 4. Reject blank, IP-only, localhost, or private-network addresses
     * 5. Reject domains without a TLD (no dot)
     * 6. Flag non-rutracker domains as suspicious ([isWarning])
     *
     * @param input Raw user input (e.g., "https://rutracker.nl/forum/", "rutracker.nl")
     * @return [ValidationResult] with sanitized domain or rejection reason
     */
    public fun validate(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(
                sanitizedDomain = null,
                isWarning = false,
                rejectionReason = "Domain must not be blank",
            )
        }

        // Strip protocol
        val withoutProtocol =
            trimmed
                .removePrefix("https://")
                .removePrefix("http://")

        // Strip path, query, fragment, port — keep only the host
        val domain =
            withoutProtocol
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringBefore(":")
                .lowercase()

        if (domain.isBlank()) {
            return ValidationResult(
                sanitizedDomain = null,
                isWarning = false,
                rejectionReason = "Domain must not be empty after sanitization",
            )
        }

        // Reject localhost / loopback
        if (domain == "localhost" || domain == "127.0.0.1" || domain.endsWith(".local")) {
            return ValidationResult(
                sanitizedDomain = null,
                isWarning = false,
                rejectionReason = "Local addresses are not valid mirror domains",
            )
        }

        // Reject private / reserved IP ranges (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
        if (isPrivateOrReservedIp(domain)) {
            return ValidationResult(
                sanitizedDomain = null,
                isWarning = false,
                rejectionReason = "Private IP addresses are not valid mirror domains",
            )
        }

        // Must contain at least one dot (TLD required)
        if (!domain.contains(".")) {
            return ValidationResult(
                sanitizedDomain = null,
                isWarning = false,
                rejectionReason = "Domain must contain a valid TLD (e.g., rutracker.nl)",
            )
        }

        // Must not contain spaces
        if (domain.contains(" ")) {
            return ValidationResult(
                sanitizedDomain = null,
                isWarning = false,
                rejectionReason = "Domain must not contain spaces",
            )
        }

        // Check if it looks like a known rutracker mirror
        val looksLikeRutracker =
            KNOWN_RUTRACKER_KEYWORDS.any { keyword -> domain.contains(keyword, ignoreCase = true) }

        return ValidationResult(
            sanitizedDomain = domain,
            isWarning = !looksLikeRutracker,
            rejectionReason =
                if (!looksLikeRutracker) {
                    "Domain does not appear to be a RuTracker mirror"
                } else {
                    null
                },
        )
    }

    /**
     * Checks whether the given domain is a raw IPv4 or IPv6 address
     * that falls within private/reserved ranges.
     */
    private fun isPrivateOrReservedIp(domain: String): Boolean {
        // Simple IPv4 check: 4 octets separated by dots
        val ipv4Parts = domain.split(".")
        if (ipv4Parts.size == 4) {
            val octets = ipv4Parts.mapNotNull { it.toIntOrNull() }
            if (octets.size == 4) {
                val (a, b, _, _) = octets
                // 10.0.0.0/8
                if (a == 10) return true
                // 172.16.0.0/12
                if (a == 172 && b in 16..31) return true
                // 192.168.0.0/16
                if (a == 192 && b == 168) return true
                // 169.254.0.0/16 (link-local)
                if (a == 169 && b == 254) return true
            }
        }

        // IPv6 loopback or link-local
        if (domain.startsWith("::1") || domain.startsWith("fe80:")) return true

        return false
    }
}
