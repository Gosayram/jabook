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

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HtmlToAnnotatedStringTest {
    @Test
    fun `convert handles basic formatting`() {
        val html = "<b>Bold</b> <i>Italic</i> <u>Underline</u>"
        val annotated = HtmlToAnnotatedString.convert(html)

        assertEquals("Bold Italic Underline", annotated.text)
        // Verify we have spans (simplified check)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun `convert identifies external links`() {
        val html = "<a href=\"https://google.com\">Google</a>"
        val annotated = HtmlToAnnotatedString.convert(html)

        assertEquals("Google", annotated.text)
        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        assertTrue(linkAnnotations[0].item is LinkAnnotation.Url)
        assertEquals("https://google.com", (linkAnnotations[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun `convert identifies internal topic links`() {
        val html = "<a href=\"viewtopic.php?t=12345\">Topic</a>"
        val annotated = HtmlToAnnotatedString.convert(html)

        assertEquals("Topic", annotated.text)
        val stringAnnotations = annotated.getStringAnnotations("TOPIC_ID", 0, annotated.length)
        assertEquals(1, stringAnnotations.size)
        assertEquals("12345", stringAnnotations[0].item)
    }

    @Test
    fun `convert handles spoilers`() {
        val html =
            """
            <div class="sp-wrap">
                <div class="sp-head">Title</div>
                <div class="sp-body">Content</div>
            </div>
            """.trimIndent()
        val annotated = HtmlToAnnotatedString.convert(html)

        assertTrue(annotated.text.contains("[ Title ]"))
        assertTrue(annotated.text.contains("Content"))
    }

    @Test
    fun `convert handles lists`() {
        val html = "<ul><li>Item 1</li><li>Item 2</li></ul>"
        val annotated = HtmlToAnnotatedString.convert(html)

        assertTrue(annotated.text.contains("• Item 1"))
        assertTrue(annotated.text.contains("• Item 2"))
    }
}
