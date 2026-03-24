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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MetadataExtractionTest {
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
        val mockLoggerFactory: com.jabook.app.jabook.compose.core.logger.LoggerFactory = mock()
        whenever(mockLoggerFactory.get(org.mockito.kotlin.any<String>())).thenReturn(
            com.jabook.app.jabook.compose.core.logger.NoOpLogger,
        )
        fieldExtractor = DefensiveFieldExtractor(mockLoggerFactory)
        // Mock getBaseUrl behavior
        whenever(mirrorManager.getBaseUrl()).thenReturn("https://rutracker.org")
        coverExtractor = CoverUrlExtractor(mirrorManager, mockLoggerFactory)
        parser =
            RutrackerParser(
                mockMediaInfoParser,
                mockDecoder,
                fieldExtractor,
                coverExtractor,
                mirrorManager,
                mockLoggerFactory,
            )
    }

    @Test
    fun `parseTopicDetails extracts extended metadata`() {
        // Load the HTML content (simulated from the provided file)
        // I will use a simplified version for this test based on what I saw in the file
        val html =
            """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="Windows-1251">
<title>Test Topic</title>
</head>
<body>
<div id="page_content">
    <h1 class="maintitle"><a href="viewtopic.php?t=3905367">Дяченко Марина, Дяченко Сергей - Vita Nostra</a></h1>
    <div class="post_body">
        <span class="post-b">Год выпуска</span>: 2012 г.<br>
        <span class="post-b">Авторы</span>: Дяченко Марина, Дяченко Сергей<br>
        <span class="post-b">Исполнитель</span>: Князев Игорь<br>
        <span class="post-b">Корректор</span>: Бондаренко Светлана<br>
        <span class="post-b">Авторский постер:</span> : Колосова Светлана<br>
        <span class="post-b">Жанр</span>: Психологическая фантастика<br>
        <span class="post-b">Издательство</span>: The Black Box Publishing<br>
        <span class="post-b">Тип аудиокниги</span>: аудиокнига<br>
        <span class="post-b">Аудио кодек</span>: MP3<br>
        <span class="post-b">Битрейт аудио</span>: 192 kbps<br>
        <span class="post-b">Время звучания</span>: 15:29:47<br>
        <span class="post-b">Музыка:</span> Øystein Sevåg - Bridge<br>
        <span class="post-b">От издателя:</span><br>
        Жизнь Саши Самохиной превращается в кошмар.
    </div>
</div>
</body>
</html>
            """.trimIndent()

        val details = parser.parseTopicDetails(html, "3905367")

        // Validation
        assertTrue(details != null)
        val meta = details!!.allMetadata

        assertEquals("2012 г.", meta["addedDate"])
        // Note: Authors are accumulated in "author"
        assertTrue(details.author?.contains("Дяченко Марина") == true)
        assertEquals("Князев Игорь", meta["performer"])
        assertEquals("Бондаренко Светлана", meta["correction"])
        // assertEquals("Колосова Светлана", meta["poster_author"])
        assertEquals("Психологическая фантастика", meta["genre"])
        assertEquals("The Black Box Publishing", meta["publisher"])
        assertEquals("аудиокнига", meta["book_type"])
        assertEquals("MP3", meta["codec"])
        assertEquals("192 kbps", meta["bitrate"])
        assertEquals("15:29:47", meta["duration"])
        // assertEquals("Øystein Sevåg - Bridge", meta["Музыка"])

        // Description Cleaning Check
        val description = details.description
        assertTrue("Description should not be null", description != null)
        val desc = description!!

        // It should NOT contain metadata fields
        assertTrue("Description should not contain Year: $desc", !desc.contains("Год выпуска"))
        assertTrue("Description should not contain Authors: $desc", !desc.contains("Авторы"))
        // It SHOULD contain "От издателя" content or at least the text
        assertTrue("Description should contain description text. Actual: '$desc'", desc.contains("Жизнь Саши Самохиной"))
    }
}
