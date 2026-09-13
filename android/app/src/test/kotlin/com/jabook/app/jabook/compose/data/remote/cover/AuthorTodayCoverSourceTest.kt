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

package com.jabook.app.jabook.compose.data.remote.cover

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthorTodayCoverSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: AuthorTodayCoverSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        source =
            AuthorTodayCoverSource(
                OkHttpClient(),
                RemoteCoverProviderTest.NoopLoggerFactory,
                searchUrlBase = server.url("/search").toString(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses result cards with title author and cover url`() =
        runBlocking {
            val coverUrl = "https://cm.author.today/content/2026/01/02/abc.jpg?width=153&height=200&rmode=max"
            server.enqueue(
                html(
                    "<html><body>" +
                        "<div class=\"search-result\">" +
                        "<a href=\"/work/123\"><img data-src=\"$coverUrl\"></a>" +
                        "<h4><a href=\"/work/123\">Война и мир</a></h4>" +
                        "<div class=\"book-author\">Лев Толстой</div>" +
                        "</div>" +
                        "</body></html>",
                ),
            )

            val candidates = source.findCandidates("Война и мир", "Лев Толстой")

            assertEquals(1, candidates.size)
            assertEquals(coverUrl, candidates.first().url)
            assertEquals("Война и мир", candidates.first().resultTitle)
            assertEquals("Лев Толстой", candidates.first().resultAuthor)
        }

    @Test
    fun `caps candidates at five`() =
        runBlocking {
            val cards =
                (1..8).joinToString("") { n ->
                    """<div class="search-result">""" +
                        """<a href="/work/$n"><img data-src="https://cm.author.today/c$n.jpg?width=153&height=200&rmode=max"></a>""" +
                        """<h4><a href="/work/$n">Заголовок $n</a></h4>""" +
                        """</div>"""
                }
            server.enqueue(html("<html><body>$cards</body></html>"))

            assertEquals(5, source.findCandidates("Тест", "Автор").size)
        }

    @Test
    fun `empty and malformed pages yield no candidates`() {
        assertTrue(source.parseCandidates("<html><body><p>no results</p></body></html>", "https://author.today").isEmpty())
        assertTrue(source.parseCandidates("<<<not html>>>", "https://author.today").isEmpty())
        assertTrue(source.parseCandidates("", "https://author.today").isEmpty())
    }

    @Test
    fun `http error yields no candidates`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))

            assertTrue(source.findCandidates("Война и мир", "Лев Толстой").isEmpty())
        }

    @Test
    fun `query encodes title and author`() =
        runBlocking {
            server.enqueue(html("<html><body></body></html>"))
            source.findCandidates("Война и мир", "Лев Толстой")

            val requested = server.takeRequest()
            assertEquals("/search", requested.path.orEmpty().substringBefore('?'))
            val query = requested.requestUrl?.queryParameter("q").orEmpty()
            assertEquals("Война и мир Лев Толстой", query)
        }

    private fun html(body: String): MockResponse = MockResponse().setHeader("Content-Type", "text/html").setBody(body)
}
