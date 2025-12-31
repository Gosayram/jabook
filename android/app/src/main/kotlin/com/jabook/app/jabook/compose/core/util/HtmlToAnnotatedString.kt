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

package com.jabook.app.jabook.compose.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.text.HtmlCompat
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Utility for converting HTML to AnnotatedString with clickable links.
 */
object HtmlToAnnotatedString {
    /**
     * Convert HTML string to AnnotatedString with clickable links.
     *
     * @param html HTML content
     * @param linkColor Color for links (default: Material Blue #2196F3)
     * @return AnnotatedString with clickable links
     */
    fun convert(
        html: String,
        linkColor: androidx.compose.ui.graphics.Color =
            androidx.compose.ui.graphics
                .Color(0xFF2196F3),
    ): AnnotatedString {
        if (html.isBlank()) {
            return AnnotatedString("")
        }

        return buildAnnotatedString {
            try {
                val doc = Jsoup.parse(html)
                processNode(doc.body(), linkColor)
            } catch (e: Exception) {
                // Fallback: use Android's Html.fromHtml
                val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
                append(spanned.toString())
            }
        }
    }

    private fun AnnotatedString.Builder.processNode(
        node: Node,
        linkColor: androidx.compose.ui.graphics.Color,
    ) {
        when (node) {
            is TextNode -> {
                append(node.text())
            }
            is Element -> {
                when (node.tagName()) {
                    "a" -> {
                        val href = node.attr("href")
                        val linkStyle =
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )

                        if (href.isNotEmpty()) {
                            val start = length
                            withStyle(linkStyle) {
                                node.childNodes().forEach { processNode(it, linkColor) }
                            }
                            val end = length
                            addLink(
                                LinkAnnotation.Url(href),
                                start = start,
                                end = end,
                            )
                        } else {
                            node.childNodes().forEach { processNode(it, linkColor) }
                        }
                    }
                    "br" -> {
                        append("\n")
                    }
                    "p" -> {
                        node.childNodes().forEach { processNode(it, linkColor) }
                        append("\n")
                    }
                    "b", "strong" -> {
                        withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                            node.childNodes().forEach { processNode(it, linkColor) }
                        }
                    }
                    "i", "em" -> {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            node.childNodes().forEach { processNode(it, linkColor) }
                        }
                    }
                    "u" -> {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            node.childNodes().forEach { processNode(it, linkColor) }
                        }
                    }
                    else -> {
                        // Process child nodes for other tags
                        node.childNodes().forEach { processNode(it, linkColor) }
                    }
                }
            }
            else -> {
                // Unknown node type, skip
            }
        }
    }
}
