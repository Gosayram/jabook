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

import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.encoding.RutrackerSimpleDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for RutrackerParser.
 *
 * Tests cover:
 * - Search result parsing with cascading selectors
 * - Field extraction (seeders, leechers, size, title)
 * - Cover URL extraction with priority system
 * - Topic details parsing
 * - Title cleaning logic
 * - Error handling and partial results
 *
 * NOTE: These tests require Robolectric or Android test framework
 * to run successfully due to android.util.Log dependencies in RutrackerParser.
 * For now, they serve as documentation of expected behavior.
 */
@RunWith(RobolectricTestRunner::class)
class RutrackerParserTest {
    private lateinit var parser: RutrackerParser
    private lateinit var mockDecoder: RutrackerSimpleDecoder
    private lateinit var mockMediaInfoParser: MediaInfoParser
    private lateinit var fieldExtractor: DefensiveFieldExtractor
    private lateinit var coverExtractor: CoverUrlExtractor
    private lateinit var mirrorManager: MirrorManager

    @Before
    fun setup() {
        mockDecoder = mock()
        mockMediaInfoParser = mock()
        mirrorManager = mock()
        fieldExtractor = DefensiveFieldExtractor()
        
        // Mock getBaseUrl behavior
        org.mockito.kotlin.whenever(mirrorManager.getBaseUrl()).thenReturn("https://rutracker.org")
        
        // CoverUrlExtractor requires MirrorManager
        coverExtractor = CoverUrlExtractor(mirrorManager)

        parser =
            RutrackerParser(
                mockMediaInfoParser,
                mockDecoder,
                fieldExtractor,
                coverExtractor,
                mirrorManager,
            )
    }

    // ============ Search Result Parsing Tests ============

    @Test
    fun `parseSearchResults extracts basic topic information`() {
        val html =
            """
            <html>
            <body>
                <table>
                <tr class="hl-tr" data-topic_id="6171728">
                    <td><a class="torTopic" href="viewtopic.php?t=6171728">Л.Н. Толстой - Война и мир</a></td>
                    <td><a class="pmed" href="profile.php?user=123">Narrator</a></td>
                    <td><span class="seed"><b>15</b></span></td>
                    <td><span class="leech"><b>3</b></span></td>
                    <td><span class="small">2.5 GB</span></td>
                </tr>
                </table>
            </body>
            </html>
            """.trimIndent()

        val results = parser.parseSearchResults(html)

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("6171728", result.topicId)
        assertEquals("Л.Н. Толстой - Война и мир", result.title)
        // Expected author is from Title (Л.Н. Толстой), not Uploader (Narrator)
        // because extractAuthorFromTitle logic prioritizes title parsing
        assertEquals("Л.Н. Толстой", result.author)
        assertEquals(15, result.seeders)
        assertEquals(3, result.leechers)
    }

    @Test
    fun `parseSearchResults handles multiple rows`() {
        val html =
            """
            <html>
            <body>
                <table>
                <tr class="hl-tr" data-topic_id="1">
                    <td><a class="torTopic" href="viewtopic.php?t=1">Book One</a></td>
                    <td><a class="pmed">Author 1</a></td>
                </tr>
                <tr class="hl-tr" data-topic_id="2">
                    <td><a class="torTopic" href="viewtopic.php?t=2">Book Two</a></td>
                    <td><a class="pmed">Author 2</a></td>
                </tr>
                </table>
            </body>
            </html>
            """.trimIndent()

        val results = parser.parseSearchResults(html)

        assertEquals(2, results.size)
        assertEquals("1", results[0].topicId)
        assertEquals("2", results[1].topicId)
    }

    @Test
    fun `parseSearchResults returns empty list on invalid HTML`() {
        val html =
            """
            <html><body><p>No search results</p></body></html>
            """.trimIndent()

        val results = parser.parseSearchResults(html)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `parseSearchResults handles missing author gracefully`() {
        val html =
            """
            <html>
            <body>
                <table>
                <tr class="hl-tr" data-topic_id="100">
                    <td><a class="torTopic" href="viewtopic.php?t=100">Test Book</a></td>
                    <td></td>
                </tr>
                </table>
            </body>
            </html>
            """.trimIndent()

        val results = parser.parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("Unknown", results[0].author)
    }

    @Test
    fun `parseSearchResults extracts uploader nickname`() {
        val html =
            """
            <html>
            <body>
                <table>
                <tr class="hl-tr" data-topic_id="555">
                    <td><a class="torTopic" href="viewtopic.php?t=555">Book Title</a></td>
                    <td><a class="topicAuthor" href="profile.php?u=777">UploaderNick</a></td>
                    <td><span class="seed">1</span></td>
                    <td><span class="leech">0</span></td>
                    <td><span class="small">1 GB</span></td>
                </tr>
                </table>
            </body>
            </html>
            """.trimIndent()

        val results = parser.parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("UploaderNick", results[0].uploader)
        // Author should fallback to uploader if not in title
        assertEquals("UploaderNick", results[0].author)
    }

    // ============ Title Cleaning Tests ============

    @Test
    fun `cleanTitle removes square brackets content`() {
        val testCases =
            listOf(
                "Book Title [1962, СССР]" to "Book Title",
                "[Format] Title [Year]" to "Title",
                "Title" to "Title",
            )

        // Use reflection to access private cleanTitle method
        val method = RutrackerParser::class.java.getDeclaredMethod("cleanTitle", String::class.java)
        method.isAccessible = true

        testCases.forEach { (input, expected) ->
            val result = method.invoke(parser, input) as String
            assertEquals(expected, result)
        }
    }

    // ============ Topic Details Parsing Tests ============

    @Test
    fun `parseTopicDetails extracts complete information`() {
        val html =
            """
            <html>
            <head><title>Topic</title></head>
            <body>
                <h1 class="maintitle"><a>Test Audiobook Title</a></h1>
                <div class="post_body">
                    <span class="post-b">Автор</span>: Test Author<br>
                    <span class="post-b">Исполнитель</span>: Test Narrator<br>
                    <span class="post-b">Жанр</span>: Fiction, Drama<br>
                    <a class="magnet-link" href="magnet:?xt=urn:btih:test123"></a>
                    <span id="tor-size-humn">1.5 GB</span>
                </div>
            </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "12345")

        assertNotNull(details)
        details?.let {
            assertEquals("12345", it.topicId)
            assertEquals("Test Audiobook Title", it.title)

            assertEquals("Test Author", it.author)
            assertEquals("Test Narrator", it.performer)
            assertEquals("1.5 GB", it.size)
            assertEquals("magnet:?xt=urn:btih:test123", it.magnetUrl)
            assertTrue(it.genres.contains("Fiction"))
            assertTrue(it.genres.contains("Drama"))
        }
    }

    @Test
    fun `parseTopicDetails returns null on invalid HTML`() {
        val html =
            """
            <html><body><p>Invalid content</p></body></html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "999")

        assertNull(details)
    }

    @Test
    fun `parseTopicDetails handles missing optional fields`() {
        val html =
            """
            <html>
            <body>
                <h1 class="maintitle"><a>Minimal Topic</a></h1>
                <div class="post_body"></div>
            </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")

        assertNotNull(details)
        details?.let {
            assertEquals("123", it.topicId)
            assertEquals("Minimal Topic", it.title)
            assertNull(it.author)
            assertNull(it.performer)
        }
    }

    @Test
    fun `parseTopicDetails truncates description at additional info`() {
        val html =
            """
            <html>
            <body>
                <h1 class="maintitle"><a>Truncation Test</a></h1>
                <div class="post_body">
                    <p>Description line 1.</p>
                    <p>Description line 2.</p>
                    <span class="post-b">Доп. информация</span>:
                    <p>Some unnecessary footer info.</p>
                </div>
            </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")

        assertNotNull(details)
        val description = details?.description ?: ""

        // Should contain lines before marker
        assertTrue(description.contains("Description line 1"))

        // Should NOT contain lines after marker
        assertEquals(-1, description.indexOf("footer info"))
    }

    @Test
    fun `parseTopicDetails extracts structural metadata`() {
        val html =
            """
            <html>
            <head><title>Topic</title></head>
            <body>
                <h1 class="maintitle"><a>Test Audiobook</a></h1>
                <div class="post_body">
                    <span class="post-b">Автор</span>: Mikhail Atamanov<br>
                    <span class="post-b">Исполнитель</span>: Kirill Zakharchuk<br>
                    <span class="post-b">Цикл/серия</span>: Dark Herbalist<br>
                    <span class="post-b">Время звучания</span>: 11:00:00<br>
                    <span class="post-b">Битрейт</span>: 64 kbps<br>
                    
                    Some description text here.
                </div>
            </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")

        assertNotNull(details)
        details?.let {
            assertEquals("Mikhail Atamanov", it.author)
            assertEquals("Kirill Zakharchuk", it.performer)
            assertEquals("Dark Herbalist", it.series)
            assertEquals("11:00:00", it.duration)
            assertEquals("64 kbps", it.bitrate)
        }
    }

    // ============ Login Response Parsing Tests ============

    @Test
    fun `parseLoginResponse detects success`() {
        val html =
            """
            <html>
            <body>
                <a href="login.php?logout=1">Выход</a>
            </body>
            </html>
            """.trimIndent()

        val result = parser.parseLoginResponse(html)

        assertTrue(result is RutrackerParser.LoginResult.Success)
    }

    @Test
    fun `parseLoginResponse detects invalid credentials`() {
        val html =
            """
            <html>
            <body>
                <p>Неверный пароль</p>
            </body>
            </html>
            """.trimIndent()

        val result = parser.parseLoginResponse(html)

        assertTrue(result is RutrackerParser.LoginResult.Error)
        assertEquals("Invalid username or password", (result as RutrackerParser.LoginResult.Error).message)
    }

    @Test
    fun `parseLoginResponse detects captcha requirement`() {
        val html =
            """
            <html>
            <body>
                <p>Введите код с картинки</p>
                <input type="hidden" name="cap_sid" value="abc123">
                <img src="//static.t-ru.org/captcha/test.jpg">
            </body>
            </html>
            """.trimIndent()

        val result = parser.parseLoginResponse(html)

        assertTrue(result is RutrackerParser.LoginResult.Captcha)
        (result as RutrackerParser.LoginResult.Captcha).let {
            assertEquals("abc123", it.data.sid)
            assertEquals("https://static.t-ru.org/captcha/test.jpg", it.data.url)
        }
    }
}
