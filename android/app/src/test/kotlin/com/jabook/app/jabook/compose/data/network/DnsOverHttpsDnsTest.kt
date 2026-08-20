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
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

/**
 * Unit tests for [DnsOverHttpsDns].
 *
 * The DNS wire-format lookup is owned by OkHttp's `DnsOverHttps`; these tests
 * verify only the fallback-to-system-DNS resilience added by this wrapper.
 */
class DnsOverHttpsDnsTest {
    private val dohAddress: InetAddress = InetAddress.getByName("93.184.216.34")
    private val fallbackAddress: InetAddress = InetAddress.getByName("203.0.113.10")

    @Test
    fun `lookup returns DoH result when available`() {
        val resolver =
            DnsOverHttpsDns(
                dohDns = Dns { listOf(dohAddress) },
                fallbackDns = Dns { listOf(fallbackAddress) },
            )

        val result = resolver.lookup("example.com")

        assertEquals(listOf(dohAddress), result)
    }

    @Test
    fun `lookup falls back to system DNS when DoH returns empty`() {
        val resolver =
            DnsOverHttpsDns(
                dohDns = Dns { emptyList() },
                fallbackDns = Dns { listOf(fallbackAddress) },
            )

        val result = resolver.lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
    }

    @Test
    fun `lookup falls back to system DNS when DoH throws`() {
        val resolver =
            DnsOverHttpsDns(
                dohDns = Dns { throw IOException("DoH unreachable") },
                fallbackDns = Dns { listOf(fallbackAddress) },
            )

        val result = resolver.lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
    }

    @Test
    fun `lookup passes hostname through to DoH resolver`() {
        var seenHostname: String? = null
        val resolver =
            DnsOverHttpsDns(
                dohDns =
                    Dns { hostname ->
                        seenHostname = hostname
                        listOf(dohAddress)
                    },
                fallbackDns = Dns { listOf(fallbackAddress) },
            )

        resolver.lookup("example.com")

        assertEquals("example.com", seenHostname)
    }
}
