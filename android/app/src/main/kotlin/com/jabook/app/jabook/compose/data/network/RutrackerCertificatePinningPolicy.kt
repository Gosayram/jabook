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

import com.jabook.app.jabook.BuildConfig
import okhttp3.CertificatePinner

/**
 * Certificate pinning policy for RuTracker mirrors.
 *
 * We pin both:
 * - leaf certificate SPKI for each known mirror
 * - current intermediate (Google Trust Services WE1) SPKI as backup
 *
 * This keeps validation strict while reducing breakage during routine leaf renewals.
 */
public object RutrackerCertificatePinningPolicy {
    // Leaf SPKI pins (captured on 2026-04-10), in the same order as the
    // canonical mirrors from .env (RUTRACKER_DEFAULT_MIRRORS).
    private const val PIN_LEAF_ORG: String = "sha256/q9Z3qXo6SZEcRaCl+/dSuiMZXX8dSrZDQC7+pZugV5U="
    private const val PIN_LEAF_NET: String = "sha256/tOFeRzloarPYX5mQ9ksIypCp36vLupuTvOo8sF4Ka2I="

    // Google Trust Services WE1 intermediate SPKI pin (backup for renewals).
    private const val PIN_INTERMEDIATE_WE1: String = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="

    // Canonical mirrors, supplied at build time from .env — never hardcoded.
    // Only the first two (which carry leaf pins) are pinned; any additional
    // custom mirrors fall back to standard validation.
    private val canonicalMirrors: List<String> =
        BuildConfig.RUTRACKER_DEFAULT_MIRRORS
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(2)

    private val leafPins: List<String> = listOf(PIN_LEAF_ORG, PIN_LEAF_NET)

    public val hostPins: Map<String, Set<String>> =
        canonicalMirrors
            .mapIndexedNotNull { index, host ->
                val leaf = leafPins.getOrNull(index) ?: return@mapIndexedNotNull null
                host to setOf(leaf, PIN_INTERMEDIATE_WE1)
            }.toMap()

    public val pinnedHosts: Set<String> = hostPins.keys

    public fun buildCertificatePinner(): CertificatePinner =
        CertificatePinner
            .Builder()
            .apply {
                hostPins.forEach { (host, pins) ->
                    add(host, *pins.toTypedArray())
                }
            }.build()
}
