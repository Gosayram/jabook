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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [HtmlSanitizer] — the parser trust boundary for RuTracker HTML fields.
 */
public class HtmlSanitizerTest {
    @Test
    public fun `null passes through`() {
        assertNull(HtmlSanitizer.sanitize(null))
    }

    @Test
    public fun `script tag is removed`() {
        val out = HtmlSanitizer.sanitize("<p>ok</p><script>alert(1)</script>")!!
        assertFalse("script tag must not survive", out.contains("<script", ignoreCase = true))
        assertTrue("text content must survive", out.contains("ok"))
    }

    @Test
    public fun `on-star attributes are stripped`() {
        val out = HtmlSanitizer.sanitize("<b onclick=\"evil()\" onmouseover=\"evil()\">x</b>")!!
        assertFalse(out.contains("onclick"))
        assertFalse(out.contains("onmouseover"))
        assertEquals("<b>x</b>", out)
    }

    @Test
    public fun `javascript href is neutralized but link text kept`() {
        val out = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>")!!
        assertFalse(out.contains("javascript"))
        assertFalse(out.contains("href"))
        assertTrue(out.contains("click"))
    }

    @Test
    public fun `http links kept with rel nofollow noopener`() {
        val out =
            HtmlSanitizer.sanitize("<a href=\"https://mirror.example/viewtopic.php?t=1\">t</a>")!!
        assertTrue(out.contains("href=\"https://mirror.example/viewtopic.php?t=1\""))
        assertTrue(out.contains("rel=\"nofollow noopener\""))
    }

    @Test
    public fun `formatting tags are preserved`() {
        val src = "<p>a <b>bo</b><i>ic</i><em>e</em><strong>s</strong>u <br>br</p><ul><li>li</li></ul>"
        val out = HtmlSanitizer.sanitize(src)!!
        listOf("<p>", "<b>", "<i>", "<em>", "<strong>", "<br>", "<ul>", "<li>").forEach {
            assertTrue("missing $it in $out", out.contains(it))
        }
    }

    @Test
    public fun `img and styles are dropped`() {
        val out =
            HtmlSanitizer.sanitize(
                "<div style=\"x\"><img src=\"https://evil.example/a.gif\" alt=\"v\">text</div>",
            )!!
        assertFalse(out.contains("<img"))
        assertFalse(out.contains("style"))
        assertTrue(out.contains("text"))
    }

    @Test
    public fun `output is capped`() {
        val out = HtmlSanitizer.sanitize("<p>" + "a".repeat(50_000) + "</p>")!!
        assertTrue("length ${out.length} must be <= ${HtmlSanitizer.MAX_OUTPUT_CHARS}", out.length <= HtmlSanitizer.MAX_OUTPUT_CHARS)
    }

    @Test
    public fun `whitespace is normalized`() {
        val out = HtmlSanitizer.sanitize("<p>a\n\n   b</p>\t\tc")!!
        assertFalse(out.contains("\n"))
        assertFalse(out.contains("  "))
    }

    @Test
    public fun `real fixture post body still renders text after sanitize`() {
        val html =
            checkNotNull(
                javaClass.classLoader?.getResource("fixtures/rutracker/parser_topic_fixture.html"),
            ) { "fixture missing" }.readText()
        val postBody =
            org.jsoup.Jsoup
                .parse(html)
                .selectFirst(".post_body")!!
                .html()
        val out = HtmlSanitizer.sanitize(postBody)!!
        assertTrue("description text must survive sanitizing", out.contains("Пикник на обочине"))
        assertFalse("foreign img CDN must be dropped", out.contains("<img"))
    }
}
