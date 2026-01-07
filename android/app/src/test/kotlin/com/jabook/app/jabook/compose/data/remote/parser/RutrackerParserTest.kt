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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        org.mockito.kotlin
            .whenever(mirrorManager.getBaseUrl())
            .thenReturn("https://rutracker.org")

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
                    <span class="post-b">Описание</span>: Description line 1. Description line 2.
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

    @Test
    fun `parseTopicDetails extracts comments with avatars`() {
        val html =
            """
            <html>
            <head><title>Topic with Comments</title></head>
            <body>
                <h1 class="maintitle"><a>Topic Title</a></h1>
                <div class="post_body">Description</div>
                
                <table>
                    <!-- Main Post (should be skipped) -->
                    <tbody id="post_main" class="row1">
                        <tr><td><div class="post_body">Main Post Content</div></td></tr>
                    </tbody>
                    
                    <!-- Comment 1 with avatar -->
                    <tbody id="post_123456" class="row1">
                        <tr>
                            <td class="poster_info td1">
                                <p class="nick"><a href="#">UserWithAvatar</a></p>
                                <p class="avatar"><img src="https://static.rutracker.cc/avatars/1/1/1234.png" alt=""></p>
                            </td>
                            <td class="message td2">
                                <div class="post_body" id="p-123456">Comment text</div>
                            </td>
                        </tr>
                    </tbody>

                    <!-- Comment 2 without avatar -->
                    <tbody id="post_789012" class="row2">
                        <tr>
                            <td class="poster_info td1">
                                <p class="nick"><a href="#">UserNoAvatar</a></p>
                            </td>
                            <td class="message td2">
                                <div class="post_body" id="p-789012">Another comment</div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "111")

        assertNotNull(details)
        assertEquals(2, details?.comments?.size)

        val comment1 = details?.comments?.get(0)
        assertEquals("UserWithAvatar", comment1?.author)
        assertEquals("https://static.rutracker.cc/avatars/1/1/1234.png", comment1?.avatarUrl)

        val comment2 = details?.comments?.get(1)
        assertEquals("UserNoAvatar", comment2?.author)
        assertNull(comment2?.avatarUrl)
    }

    @Test
    fun `parseTopicDetails extracts registered date and download count`() {
        val html =
            """
            <html>
                <head><title>Stats Test</title></head>
                <body>
                    <h1 class="maintitle"><a>Stats Test</a></h1>
                    <div class="post_body">Body</div>
                    <table>
                        <tr class="row1">
                             <td>Зарегистрирован</td>
                             <td>
                                <ul>
                                    <li>21-Май-19 15:42</li>
                                    <li>Скачан: 11,783 раза</li>
                                </ul>
                             </td>
                        </tr>
                    </table>
                </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")
        assertNotNull(details)
        assertEquals("21-Май-19 15:42", details?.registeredDate)
        assertEquals("11,783", details?.downloadsCount)
    }

    @Test
    fun `parseTopicDetails extracts pagination info`() {
        val html =
            """
            <html>
                <head><title>Pagination Test</title></head>
                <body>
                    <h1 class="maintitle"><a>Topic Title</a></h1>
                    <div id="pagination">
                        <p>Страница 2 из 10</p>
                    </div>
                    <div class="post_body">Post body</div>
                </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")
        assertNotNull(details)
        assertEquals(2, details?.currentPage)
        assertEquals(10, details?.totalPages)
    }

    @Test
    fun `parseTopicDetails defaults pagination to 1`() {
        val html =
            """
            <html>
                <head><title>No Pagination Test</title></head>
                <body>
                    <h1 class="maintitle"><a>Topic Title</a></h1>
                    <div class="post_body">Post body</div>
                </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")
        assertNotNull(details)
        assertEquals(1, details?.currentPage)
        assertEquals(1, details?.totalPages)
    }

    @Test
    fun `parseTopicDetails extracts last page pagination`() {
        val html =
            """
            <html>
                <head><title>Last Page Test</title></head>
                <body>
                    <h1 class="maintitle"><a>Topic Title</a></h1>
                    <table id="pagination" class="topic">
                        <tr>
                            <td class="nav pad_6 row1">
                                <p style="float: left">Страница <b>30</b> из <b>30</b></p>
                            </td>
                        </tr>
                    </table>
                    <div class="post_body">Post body</div>
                </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "123")
        assertNotNull(details)
        assertEquals(30, details?.currentPage)
        assertEquals(30, details?.totalPages)
    }

    // ============ Forum Page Parsing Tests ============

    @Test
    fun `parseForumPageWithPagination extracts topics from forum page`() {
        val html =
            """
            <html>
                <head><title>Forum Page Test</title></head>
                <body>
                    <table class="vf-table forumline">
                        <tr id="tr-6795747" class="hl-tr" data-topic_id="6795747">
                            <td class="vf-col-icon"></td>
                            <td class="vf-col-t-title">
                                <div class="torTopic">
                                    <a id="tt-6795747" href="viewtopic.php?t=6795747" class="torTopic bold tt-text">Панежин Евгений – Проклятая заимка [2025, 128 kbps, MP3]</a>
                                </div>
                                <div class="topicAuthor">
                                    <a href="profile.php?mode=viewprofile&amp;u=22098407" class="topicAuthor">Gefestel</a>
                                </div>
                            </td>
                            <td class="vf-col-tor">
                                <span class="seedmed"><b>5</b></span>
                                <span class="leechmed"><b>3</b></span>
                                <a href="dl.php?t=6795747" class="f-dl">239.3 MB</a>
                            </td>
                        </tr>
                        <tr id="tr-6257322" class="hl-tr" data-topic_id="6257322">
                            <td class="vf-col-icon"></td>
                            <td class="vf-col-t-title">
                                <div class="torTopic">
                                    <a id="tt-6257322" href="viewtopic.php?t=6257322" class="torTopic bold tt-text">Дроздов Анатолий – Мастеровой [2022, 80 kbps, MP3]</a>
                                </div>
                                <div class="topicAuthor">
                                    <a href="profile.php?mode=viewprofile&amp;u=24304817" class="topicAuthor">Аудиокниги</a>
                                </div>
                            </td>
                            <td class="vf-col-tor">
                                <span class="seedmed"><b>45</b></span>
                                <span class="leechmed"><b>3</b></span>
                                <a href="dl.php?t=6257322" class="f-dl">314 MB</a>
                            </td>
                        </tr>
                    </table>
                    <div id="pagination">
                        <p>Страница <b>1</b> из <b>356</b></p>
                        <a class="pg" href="viewforum.php?f=2387&amp;start=50">След.</a>
                    </div>
                </body>
            </html>
            """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        whenever(mockDecoder.decode(any(), anyOrNull())).thenReturn(html)
        val mockResponseBody =
            bytes.toResponseBody(
                "text/html; charset=utf-8".toMediaType(),
            )

        val result = parser.parseForumPageWithPagination(mockResponseBody, "2387")

        assertTrue("Should have topics", result.topics.isNotEmpty())
        assertEquals(2, result.topics.size)
        assertTrue("Should have more pages", result.hasMorePages)

        // Verify first topic
        val firstTopic = result.topics.first()
        assertEquals("6795747", firstTopic.topicId)
        assertTrue(firstTopic.title.contains("Проклятая заимка"))
    }

    @Test
    fun `parseForumPageWithPagination detects last page correctly`() {
        val html =
            """
            <html>
                <head><title>Last Page Test</title></head>
                <body>
                    <table class="vf-table forumline">
                        <tr id="tr-5473836" class="hl-tr" data-topic_id="5473836">
                            <td class="vf-col-icon"></td>
                            <td class="vf-col-t-title">
                                <div class="torTopic">
                                    <a id="tt-5473836" href="viewtopic.php?t=5473836" class="torTopic bold tt-text">Чубарова Алёна - Смежная Зона [2017, 128 kbps, MP3]</a>
                                </div>
                            </td>
                        </tr>
                    </table>
                    <div id="pagination">
                        <p>Страница <b>356</b> из <b>356</b></p>
                        <a class="pg" href="viewforum.php?f=2387&amp;start=17700">Пред.</a>
                    </div>
                </body>
            </html>
            """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        whenever(mockDecoder.decode(any(), anyOrNull())).thenReturn(html)
        val mockResponseBody =
            bytes.toResponseBody(
                "text/html; charset=utf-8".toMediaType(),
            )

        val result = parser.parseForumPageWithPagination(mockResponseBody, "2387")

        assertTrue("Should have topics", result.topics.isNotEmpty())
        assertEquals(1, result.topics.size)
        assertEquals(false, result.hasMorePages) // Last page - no more pages!
    }

    @Test
    fun `parseForumPageWithPagination detects hasMorePages via Next link`() {
        val html =
            """
            <html>
                <head><title>Next Link Test</title></head>
                <body>
                    <table class="vf-table forumline">
                        <tr id="tr-123456" class="hl-tr" data-topic_id="123456">
                            <td class="vf-col-t-title">
                                <div class="torTopic">
                                    <a id="tt-123456" href="viewtopic.php?t=123456" class="torTopic bold tt-text">Test Topic</a>
                                </div>
                            </td>
                        </tr>
                    </table>
                    <a class="pg" href="viewforum.php?f=2387&amp;start=50">След.</a>
                </body>
            </html>
            """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        whenever(mockDecoder.decode(any(), anyOrNull())).thenReturn(html)
        val mockResponseBody =
            bytes.toResponseBody(
                "text/html; charset=utf-8".toMediaType(),
            )

        val result = parser.parseForumPageWithPagination(mockResponseBody, "2387")

        assertTrue("Should detect more pages via 'След.' link", result.hasMorePages)
    }

    @Test
    fun `parseForumPageWithPagination extracts seeders and leechers`() {
        val html =
            """
            <html>
                <body>
                    <table class="forumline">
                        <tr id="tr-100" class="hl-tr" data-topic_id="100">
                            <td class="vf-col-t-title">
                                <div class="torTopic">
                                    <a id="tt-100" href="viewtopic.php?t=100" class="torTopic tt-text">Test</a>
                                </div>
                            </td>
                            <td class="vf-col-tor">
                                <span class="seedmed" title="Seeders"><b>235</b></span>
                                <span class="leechmed" title="Leechers"><b>26</b></span>
                                <a href="dl.php?t=100" class="f-dl">566.4 MB</a>
                            </td>
                        </tr>
                    </table>
                </body>
            </html>
            """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        whenever(mockDecoder.decode(any(), anyOrNull())).thenReturn(html)
        val mockResponseBody =
            bytes.toResponseBody(
                "text/html; charset=utf-8".toMediaType(),
            )

        val result = parser.parseForumPageWithPagination(mockResponseBody, "test")

        assertEquals(1, result.topics.size)
        val topic = result.topics.first()
        assertEquals(235, topic.seeders)
        assertEquals(26, topic.leechers)
        assertTrue(topic.size.contains("566"))
    }

    @Test
    fun `parseForumPageWithPagination handles middle page correctly`() {
        val html =
            """
            <html>
                <body>
                    <table class="forumline">
                        <tr id="tr-999" class="hl-tr" data-topic_id="999">
                            <td class="vf-col-t-title">
                                <div class="torTopic">
                                    <a id="tt-999" href="viewtopic.php?t=999" class="tt-text">Middle Page Topic</a>
                                </div>
                            </td>
                        </tr>
                    </table>
                    <div id="pagination" class="nav">
                        <p style="float: left">Страница <b>178</b> из <b>356</b></p>
                        <a class="pg" href="viewforum.php?start=8850">Пред.</a>
                        <a class="pg" href="viewforum.php?start=8950">След.</a>
                    </div>
                </body>
            </html>
            """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        whenever(mockDecoder.decode(any(), anyOrNull())).thenReturn(html)
        val mockResponseBody =
            bytes.toResponseBody(
                "text/html; charset=utf-8".toMediaType(),
            )

        val result = parser.parseForumPageWithPagination(mockResponseBody, "test")

        assertTrue("Middle page should have more pages", result.hasMorePages)
    }

    // ============ Description Extraction Tests ============

    @Test
    fun `parseTopicDetails extracts description when at end of post`() {
        // This test verifies the fix for description extraction when "Описание:" is at the end
        val html =
            """
            <html>
                <head><title>Test Topic</title></head>
                <body>
                    <h1 class="maintitle"><a>Атаманов Михаил - Забаненный [2025, MP3]</a></h1>
                    <div class="post_body">
                        <span class="post-b">Год выпуска</span>: 2025<br>
                        <span class="post-b">Фамилия автора</span>: Атаманов<br>
                        <span class="post-b">Имя автора</span>: Михаил<br>
                        <span class="post-b">Исполнитель</span>: Анатолий Константинов<br>
                        <span class="post-b">Жанр</span>: ЛитРПГ<br>
                        <span class="post-b">Описание</span>: Михаил Атаманов – популярный писатель-фантаст. Предлагаем полностью погрузиться в прослушивание аудиокниги!<br>
                        <a href="#">Цикл «Забаненный»</a><br>
                        Забаненный. Книга 1<br>
                        Забаненный. Книга 2
                    </div>
                    <span id="tor-size-humn">369.9 MB</span>
                </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "6708781")

        assertNotNull(details)
        // The description should contain the actual description text
        assertTrue(
            "Description should contain 'популярный писатель-фантаст'",
            details?.description?.contains("популярный писатель-фантаст") == true,
        )
        // Description should NOT contain metadata fields
        assertTrue(
            "Description should NOT contain 'Фамилия автора'",
            details?.description?.contains("Фамилия автора") != true,
        )
    }

    @Test
    fun `parseTopicDetails handles old book format from 2014`() {
        // Test old book format with:
        // - Тип издания, Категория (instead of modern fields)
        // - Описание and Доп. информация on same line
        // - No Цикл/серия, Издательство
        val html =
            """
            <html>
                <head><title>Old Book</title></head>
                <body>
                    <h1 class="maintitle"><a>Анин Владимир - Манагуанда [2014, MP3]</a></h1>
                    <div class="post_body">
                        <span class="post-b">Год выпуска</span>: 2014 г.<br>
                        <span class="post-b">Фамилия автора</span>: Анин<br>
                        <span class="post-b">Имя автора</span>: Владимир<br>
                        <span class="post-b">Исполнитель</span>: Богдан Скромный<br>
                        <span class="post-b">Жанр</span>: Ужасы, мистика<br>
                        <span class="post-b">Тип издания</span>: аудиокнига своими руками<br>
                        <span class="post-b">Категория</span>: аудиокнига<br>
                        <span class="post-b">Описание</span>: Молодой солдат знакомится с библиотекаршей.<span class="post-br"><br></span><span class="post-b">Доп. информация</span>: Релиз Книжного трекера.
                    </div>
                    <span id="tor-size-humn">106.6 MB</span>
                </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "4882867")

        assertNotNull(details)

        // Verify author extracted (Surname + Name combined)
        assertEquals("Анин Владимир", details?.author)

        // Verify performer extracted
        assertEquals("Богдан Скромный", details?.performer)

        // Verify year extracted (Note: it includes 'г.' in old format)
        assertEquals("2014 г.", details?.addedDate)

        // Verify genre extracted
        assertTrue(details?.genres?.contains("Ужасы") == true)
        assertTrue(details?.genres?.contains("мистика") == true)

        // Verify description extracted, truncated at "Доп. информация"
        val description = details?.description ?: ""
        assertTrue("Should contain description text", description.contains("Молодой солдат"))
        assertTrue("Should NOT contain Доп. информация", !description.contains("Релиз Книжного трекера"))
    }

    @Test
    fun `parseTopicDetails handles modern listing with VBR and preserves content`() {
        val html =
            """
            <html>
            <body>
                <h1 class="maintitle">
                    <a id="topic-title" href="https://rutracker.net/forum/viewtopic.php?t=5532748">Атаманов Михаил – Искажающие реальность 1</a>
                </h1>
                <div class="post_body" id="p-74963777">
                    <span style="font-size: 24px;">Искажающие реальность Книга 1</span><br>
                    <span class="post-b">Год выпуска</span>: 2018<br>
                    <span class="post-b">Битрейт</span>: 56 kbps<br>
                    <span class="post-b">Вид битрейта</span>: переменный битрейт (VBR)<br>
                    <span class="post-b">Описание</span>: Случившийся долгожданный Первый Контакт...<br>
                    <div class="sp-wrap">
                        <div class="sp-head folded"><span>Содержание:</span></div>
                        <div class="sp-body">Глава первая. Сетевой турнир</div>
                    </div>
                    <span class="post-b">Цикл «Искажающие реальность»:</span>
                    <ol type="1">
                        <li><span class="post-b"><a href="viewtopic.php?t=5603017" class="postLink">Искажающие реальность Книга 2</a></span></li>
                    </ol>
                </div>
            </body>
            </html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "5532748")

        // Bitrate should be 56 kbps, not VBR
        assertNotNull(details)
        assertEquals("56 kbps", details!!.bitrate)

        // Description should preserve the spoiler and links in HTML
        assertNotNull(details.descriptionHtml)
        assertTrue("Should contain Content spoiler", details.descriptionHtml?.contains("Содержание") == true)
        assertTrue("Should contain Book 2 link", details.descriptionHtml?.contains("viewtopic.php?t=5603017") == true)

        // Plain text description should also contain some content if extractDescriptionSection worked
        assertTrue("Should contain description text", details.description?.contains("Случившийся долгожданный") == true)
    }
}
