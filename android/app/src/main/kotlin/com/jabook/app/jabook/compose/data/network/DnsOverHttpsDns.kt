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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS-over-HTTPS resolver using Google Public DNS.
 *
 * Bypasses ISP DNS blocks (common in Russia for torrent sites) without VPN.
 * Falls back to system DNS if DoH fails.
 *
 * Uses JSON API: https://dns.google/resolve?name=example.com&type=A
 */
public class DnsOverHttpsDns(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build(),
    private val dohEndpoint: HttpUrl = "https://dns.google/resolve".toHttpUrl(),
    private val fallbackDns: Dns = Dns.SYSTEM,
) : Dns {

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        // Try DoH first
        try {
            val dohResult = resolveViaDoH(hostname)
            if (dohResult.isNotEmpty()) return dohResult
        } catch (_: Exception) {
            // DoH failed, fall through to system DNS
        }

        // Fallback to system DNS
        return fallbackDns.lookup(hostname)
    }

    private fun resolveViaDoH(hostname: String): List<InetAddress> {
        val url =
            dohEndpoint
                .newBuilder()
                .addQueryParameter("name", hostname)
                .addQueryParameter("type", "A")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val bodyString = response.body.string()
                val json = JSONObject(bodyString)

                // Check status (0 = NOERROR)
                val status = json.optInt("Status", -1)
                if (status != 0) return emptyList()

                val answers = json.optJSONArray("Answer") ?: return emptyList()
                val results = mutableListOf<InetAddress>()

                for (i in 0 until answers.length()) {
                    val answer = answers.getJSONObject(i)
                    val type = answer.optInt("type", 0)
                    val data = answer.optString("data", "")
                    // Type 1 = A record, Type 28 = AAAA
                    if ((type == 1 || type == 28) && data.isNotEmpty()) {
                        try {
                            results.add(InetAddress.getByName(data))
                        } catch (_: Exception) {
                            // Skip invalid addresses
                        }
                    }
                }

                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
