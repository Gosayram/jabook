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

package com.jabook.app.jabook.compose.data.remote.parser

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Sanitizes HTML fields extracted from RuTracker forum pages before they reach the UI.
 *
 * Trust boundary: `descriptionHtml` and comment `html` may be rendered via
 * `AnnotatedString.withStyle`/`fromHtml` downstream, so attacker-controlled markup
 * must be reduced to plain text formatting at the parser exit — the partial
 * element removal in the parser (processCommentHtml/cleanDescriptionHtml) is not
 * enough: it leaves `on*` attributes and `javascript:` hrefs intact.
 */
internal object HtmlSanitizer {
    /** Descriptions are attacker-influenced in size; cap keeps fromHtml and RAM bounded. */
    internal const val MAX_OUTPUT_CHARS = 20_000

    /**
     * Text formatting only. No img/styles/iframes; links restricted to http(s)
     * (kills `javascript:`/`data:` hrefs) and forced rel="nofollow noopener".
     * `u` and `blockquote` are kept because the comment quote transform emits them.
     */
    private val SAFELIST: Safelist =
        Safelist
            .none()
            .addTags("b", "i", "em", "strong", "u", "a", "br", "p", "ul", "ol", "li", "blockquote")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https")
            .addEnforcedAttribute("a", "rel", "nofollow noopener")

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Null-passthrough; otherwise Jsoup.clean + whitespace normalize + length cap.
     */
    public fun sanitize(html: String?): String? {
        if (html == null) return null
        var cleaned = WHITESPACE_REGEX.replace(Jsoup.clean(html, SAFELIST), " ").trim()
        if (cleaned.length > MAX_OUTPUT_CHARS) {
            // Cut at the last complete tag below the cap so output stays tag-balanced.
            val cut = cleaned.lastIndexOf('>', MAX_OUTPUT_CHARS - 1)
            cleaned = if (cut >= 0) cleaned.substring(0, cut + 1) else cleaned.take(MAX_OUTPUT_CHARS)
        }
        return cleaned
    }
}
