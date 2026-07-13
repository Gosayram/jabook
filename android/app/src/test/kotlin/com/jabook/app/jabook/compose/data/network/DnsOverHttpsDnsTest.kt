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

package com.jabook.app.jabook.compose.data.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class DnsOverHttpsDnsTest {
    private lateinit var server: MockWebServer
    private val fallbackAddress: InetAddress = InetAddress.getByName("203.0.113.10")
    private var fallbackLookups = 0
    private val fallbackDns =
        Dns { hostname ->
            fallbackLookups++
            assertEquals("example.com", hostname)
            listOf(fallbackAddress)
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fallbackLookups = 0
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lookup returns IPv4 and IPv6 answers from DoH JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/dns-json; charset=utf-8")
                .setBody(
                    """
                    {
                      "Status": 0,
                      "Answer": [
                        {"name": "example.com.", "type": 1, "data": "93.184.216.34"},
                        {"name": "example.com.", "type": 28, "data": "2606:2800:220:1:248:1893:25c8:1946"}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = resolver().lookup("example.com")

        val addresses = result.map(InetAddress::getHostAddress)
        assertTrue("Expected IPv4 in $addresses", addresses.contains("93.184.216.34"))
        assertTrue("Expected Inet4Address in $result", result.any { it is Inet4Address })
        assertTrue("Expected Inet6Address in $result", result.any { it is Inet6Address })
        assertEquals(0, fallbackLookups)
    }

    @Test
    fun `lookup falls back to system DNS delegate on HTTP failure`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = resolver().lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
        assertEquals(1, fallbackLookups)
    }

    @Test
    fun `lookup falls back to system DNS delegate on network exception`() {
        server.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )

        val result = resolver().lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
        assertEquals(1, fallbackLookups)
    }

    @Test
    fun `lookup falls back to system DNS delegate when DoH status is non zero`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"Status": 3, "Answer": []}"""),
        )

        val result = resolver().lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
        assertEquals(1, fallbackLookups)
    }

    @Test
    fun `lookup falls back to system DNS delegate on malformed DoH JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not-json"),
        )

        val result = resolver().lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
        assertEquals(1, fallbackLookups)
    }

    @Test
    fun `lookup uses expected DoH query parameters`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"Status": 0, "Answer": []}"""),
        )

        resolver().lookup("example.com")

        val request = server.takeRequest()
        assertEquals("/resolve?name=example.com&type=A", request.path)
        assertTrue(request.getHeader("Accept") == "application/dns-json")
    }

    private fun resolver(): DnsOverHttpsDns =
        DnsOverHttpsDns(
            client = OkHttpClient(),
            dohEndpoint = server.url("/resolve"),
            fallbackDns = fallbackDns,
        )
}
