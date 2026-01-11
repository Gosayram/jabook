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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.jabook.app.jabook.compose.core.util.HtmlToAnnotatedString
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * Represents a block of content parsed from HTML.
 */
public sealed interface DescriptionBlock {
    /**
     * Standard text content (rich text).
     */
    public data class Text(
        public val content: AnnotatedString,
    ) : DescriptionBlock

    /**
     * Collapsible spoiler block.
     */
    public data class Spoiler(
        public val title: AnnotatedString,
        public val content: List<DescriptionBlock>,
    ) : DescriptionBlock
}

/**
 * Utility for parsing HTML into structured blocks (Text and Spoilers).
 */
public object HtmlBlockParser {
    /**
     * Parse HTML string into a list of DescriptionBlocks.
     */
    public fun parse(
        html: String,
        linkColor: Color = Color(0xFF2196F3),
    ): List<DescriptionBlock> {
        if (html.isBlank()) return emptyList()

        return try {
            val doc = Jsoup.parse(html)
            parseNodes(doc.body().childNodes(), linkColor)
        } catch (e: Exception) {
            // Fallback: return as single text block
            listOf(DescriptionBlock.Text(HtmlToAnnotatedString.convert(html, linkColor)))
        }
    }

    private fun parseNodes(
        nodes: List<Node>,
        linkColor: Color,
    ): List<DescriptionBlock> {
        val blocks = mutableListOf<DescriptionBlock>()

        // Accumulate text nodes to minimize fragmentation
        val currentTextHtml = StringBuilder()

        public fun flushText(...) {
            if (currentTextHtml.isNotEmpty()) {
                val text = HtmlToAnnotatedString.convert(currentTextHtml.toString(), linkColor)
                if (text.isNotEmpty()) {
                    blocks.add(DescriptionBlock.Text(text))
                }
                currentTextHtml.clear()
            }
        }

        for (node in nodes) {
            if (node is Element && node.tagName() == "div" && node.hasClass("sp-wrap")) {
                // Flush pending text before starting a spoiler
                flushText()

                // Parse spoiler
                val spoiler = parseSpoiler(node, linkColor)
                if (spoiler != null) {
                    blocks.add(spoiler)
                } else {
                    // Fallback if parsing failed: treat as normal text
                    currentTextHtml.append(node.outerHtml())
                }
            } else if (node is Element && node.tagName() == "br") {
                // Explicitly handle breaks to avoid eating them
                currentTextHtml.append("<br>")
            } else {
                // Accumulate other nodes as HTML string for batch conversion
                currentTextHtml.append(node.outerHtml())
            }
        }

        // Final flush
        flushText()

        return blocks
    }

    private fun parseSpoiler(
        element: Element,
        linkColor: Color,
    ): DescriptionBlock.Spoiler? {
        val head = element.selectFirst(".sp-head") ?: return null
        val body = element.selectFirst(".sp-body") ?: return null

        val title = HtmlToAnnotatedString.convert(head.html(), linkColor)
        val content = parseNodes(body.childNodes(), linkColor)

        return DescriptionBlock.Spoiler(title, content)
    }
}
