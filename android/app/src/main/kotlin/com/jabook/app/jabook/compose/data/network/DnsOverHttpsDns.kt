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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
                val json = Json.parseToJsonElement(bodyString).jsonObject

                // Check status (0 = NOERROR)
                val status = json["Status"]?.jsonPrimitive?.intOrNull ?: return emptyList()
                if (status != 0) return emptyList()

                val answers = json["Answer"]?.jsonArray ?: return emptyList()
                val results = mutableListOf<InetAddress>()

                for (answer in answers) {
                    val answerObject = runCatching { answer.jsonObject }.getOrNull() ?: continue
                    val type = answerObject["type"]?.jsonPrimitive?.intOrNull ?: continue
                    val data = answerObject["data"]?.jsonPrimitive?.contentOrNull ?: continue
                    // Type 1 = A record, Type 28 = AAAA
                    if (type == 1 || type == 28) {
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
