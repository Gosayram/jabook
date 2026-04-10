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
    private const val MIRROR_ORG: String = "rutracker.org"
    private const val MIRROR_NET: String = "rutracker.net"
    private const val MIRROR_ME: String = "rutracker.me"

    // Leaf SPKI pins (captured on 2026-04-10).
    private const val PIN_LEAF_ORG: String = "sha256/q9Z3qXo6SZEcRaCl+/dSuiMZXX8dSrZDQC7+pZugV5U="
    private const val PIN_LEAF_NET: String = "sha256/tOFeRzloarPYX5mQ9ksIypCp36vLupuTvOo8sF4Ka2I="
    private const val PIN_LEAF_ME: String = "sha256/ZuCuZ21OXRQ25WiUEaFMVGLLtfQCStXSLfkrpRn5fX8="

    // Google Trust Services WE1 intermediate SPKI pin (backup for renewals).
    private const val PIN_INTERMEDIATE_WE1: String = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="

    public val hostPins: Map<String, Set<String>> =
        mapOf(
            MIRROR_ORG to setOf(PIN_LEAF_ORG, PIN_INTERMEDIATE_WE1),
            "*.rutracker.org" to setOf(PIN_LEAF_ORG, PIN_INTERMEDIATE_WE1),
            MIRROR_NET to setOf(PIN_LEAF_NET, PIN_INTERMEDIATE_WE1),
            "*.rutracker.net" to setOf(PIN_LEAF_NET, PIN_INTERMEDIATE_WE1),
            MIRROR_ME to setOf(PIN_LEAF_ME, PIN_INTERMEDIATE_WE1),
            "*.rutracker.me" to setOf(PIN_LEAF_ME, PIN_INTERMEDIATE_WE1),
        )

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