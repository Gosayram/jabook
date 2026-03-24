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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Validates parsing of Rutracker quote blocks.
 */
@RunWith(RobolectricTestRunner::class)
class QuoteParsingTest {
    private lateinit var parser: RutrackerParser
    private lateinit var mockDecoder: RutrackerSimpleDecoder
    private val mirrorManager: MirrorManager = mock()

    // The problematic HTML snippet from the user
    private val quoteHtml =
        """
        <div class="q-head"><span><b>That1987</b> писал(а):</span></div>
        <div class="q"><u class="q-post">77595503</u>Из Атаманова мог бы выйти неплохой писатель. Есть хорошие и временами удачные попытки придумать повороты и неожиданные решения. Если бы не несколько но:<br>
        1) Все ГГ автора — сам Атаманов. Во всех циклах это один и тот же персонаж с одними и теми же достоинствами и недостатками, а также комплексами.<br>
        2) Отсюда крайне странное понимание женщин и стремление к гаремам. Соблазнительные фигурки соблазнительных красавиц с соблазнительными выпуклостями в соблазнительных местах повсеместны. Фурри, гоблины, богатейшие девушки планеты — всех хочет трахнуть Атаманов, а потому все женские персонажи вешаются на шею ГГ. Спасибо хоть на сестру в этом цикле не облизывался.<br>
        3) Как обычно автор одновременно любит пресмыкаться, и любит когда пресмыкаются перед ним. Лучше бы он не выбирал персонажей, которым нужно быть харизматичными и наделенными лидерскими задатками.<span class="post-br"><br></span>Легкое развлекательное чтиво, которое временами вызывает "рукалицо". Начитка неплохая. Засыпается под это дело весьма хорошо, а пропущенные моменты необязательно переслушивать для понимания происходящего.</div>
        """.trimIndent()

    @Before
    fun setup() {
        mockDecoder = mock()
        val mockLoggerFactory: com.jabook.app.jabook.compose.core.logger.LoggerFactory = mock()
        whenever(mockLoggerFactory.get(org.mockito.kotlin.any<String>())).thenReturn(
            com.jabook.app.jabook.compose.core.logger.NoOpLogger,
        )
        whenever(mirrorManager.getBaseUrl()).thenReturn("https://rutracker.org")

        parser =
            RutrackerParser(
                decoder = mockDecoder,
                fieldExtractor = DefensiveFieldExtractor(mockLoggerFactory),
                mediaInfoParser = mock(),
                mirrorManager = mirrorManager,
                coverExtractor = CoverUrlExtractor(mirrorManager, mockLoggerFactory),
                loggerFactory = mockLoggerFactory,
            )
    }

    @Test
    fun `parseTopicDetails converts quotes to blockquotes`() {
        // Wrap the quote in a valid comment structure
        val fullHtml =
            """
            <html>
            <body>
                <h1 class="maintitle"><a id="topic-title" href="viewtopic.php?t=123">Test Topic</a></h1>
                <table class="topic" id="topic_main">
                    <tbody id="post_1">
                       <tr><td><div class="post_body" id="p-1">Main Post</div></td></tr>
                    </tbody>
                    <tbody id="post_123">
                       <tr>
                           <td>
                               <p class="nick"><a href="#">User</a></p>
                               <div class="post_body" id="p-123">$quoteHtml</div>
                           </td>
                       </tr>
                    </tbody>
                </table>
            </body>
            </html>
            """.trimIndent()

        // Parse (topicId doesn't matter much here)
        val details = parser.parseTopicDetails(fullHtml, "123")

        // Assert we found the comment
        assertEquals(1, details?.comments?.size)
        val comment = details?.comments?.first()

        // Assert transformation
        assertTrue("Should contain blockquote", comment?.html?.contains("<blockquote>") == true)
        assertTrue("Should contain bold header", comment?.html?.contains("That1987 wrote:") == true)
        // Assert internal ID removal
        assertTrue("Should NOT contain internal ID 77595503", comment?.html?.contains("77595503") == false)
    }

    @Test
    fun `parseTopicDetails handles signatures and fixes relative links`() {
        val signatureHtml =
            """
            <div class="signature hide-for-print">
                <div class="sig-body">
                    <a href="search.php?uid=123" class="postLink">Link</a>
                </div>
            </div>
            """.trimIndent()

        val fullHtml =
            """
            <html>
            <body>
                <h1 class="maintitle"><a id="topic-title" href="viewtopic.php?t=123">Test Topic</a></h1>
                <table class="topic" id="topic_main">
                    <tbody id="post_1"><tr><td><div class="post_body">Main</div></td></tr></tbody>
                    <tbody id="post_2">
                       <tr>
                           <td>
                               <p class="nick"><a href="#">User</a></p>
                               <div class="post_body" id="p-2">Comment must be longer than ten chars</div>
                               $signatureHtml
                           </td>
                       </tr>
                    </tbody>
                </table>
            </body>
            </html>
            """.trimIndent()

        whenever(mockDecoder.decode(any(), any())).thenReturn(fullHtml)

        val details = parser.parseTopicDetails(fullHtml, "123")
        val comment = details?.comments?.first()

        // Assert signature is present
        assertTrue("Signature should be present", comment?.html?.contains("sig-body") == true)

        // Assert link is absolute and correct
        // getBaseUrl returns https://rutracker.org/forum/
        // So link should be https://rutracker.org/forum/search.php?uid=123
        assertTrue(
            "Link should be absolute to forum",
            comment?.html?.contains("https://rutracker.org/forum/search.php?uid=123") == true,
        )
    }
}
