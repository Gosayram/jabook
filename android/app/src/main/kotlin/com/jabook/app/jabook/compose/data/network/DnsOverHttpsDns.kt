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
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS resolver that bypasses ISP DNS blocks (common for torrent sites)
 * without a VPN, falling back to system DNS if DoH is unavailable.
 *
 * The DNS wire-format lookup is delegated to OkHttp's [DnsOverHttps]; this class
 * only adds the fallback-to-system-DNS resilience that OkHttp does not provide.
 */
public class DnsOverHttpsDns(
    private val dohDns: Dns,
    private val fallbackDns: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        try {
            dohDns.lookup(hostname).ifEmpty { fallbackDns.lookup(hostname) }
        } catch (_: Exception) {
            fallbackDns.lookup(hostname)
        }

    public companion object {
        /**
         * Builds the default resolver backed by Google Public DNS over HTTPS.
         *
         * The DoH client resolves the DoH endpoint itself via system DNS (OkHttp's
         * [DnsOverHttps.Builder.systemDns] default), avoiding any circular dependency.
         */
        public fun create(): DnsOverHttpsDns {
            val dohClient =
                OkHttpClient
                    .Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()
            val doh =
                DnsOverHttps
                    .Builder()
                    .client(dohClient)
                    .url(DOH_ENDPOINT)
                    .build()
            return DnsOverHttpsDns(dohDns = doh, fallbackDns = Dns.SYSTEM)
        }

        private val DOH_ENDPOINT: HttpUrl = "https://dns.google/dns-query".toHttpUrl()
    }
}
