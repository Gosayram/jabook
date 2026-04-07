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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.crash.CrashDiagnostics
import okhttp3.Call
import okhttp3.EventListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class NetworkCallMetricsSnapshot(
    val dnsMs: Long?,
    val connectMs: Long?,
    val tlsMs: Long?,
    val ttfbMs: Long?,
    val totalMs: Long,
)

internal class NetworkCallMetricsTracker(
    private val nowNsProvider: () -> Long = { System.nanoTime() },
) {
    private var callStartNs: Long = -1L
    private var callEndNs: Long = -1L

    private var dnsStartNs: Long = -1L
    private var dnsEndNs: Long = -1L

    private var connectStartNs: Long = -1L
    private var connectEndNs: Long = -1L

    private var tlsStartNs: Long = -1L
    private var tlsEndNs: Long = -1L

    private var requestHeadersStartNs: Long = -1L
    private var responseHeadersStartNs: Long = -1L

    fun onCallStart() {
        callStartNs = nowNsProvider()
    }

    fun onCallEnd() {
        callEndNs = nowNsProvider()
    }

    fun onDnsStart() {
        if (dnsStartNs < 0) dnsStartNs = nowNsProvider()
    }

    fun onDnsEnd() {
        dnsEndNs = nowNsProvider()
    }

    fun onConnectStart() {
        if (connectStartNs < 0) connectStartNs = nowNsProvider()
    }

    fun onConnectEnd() {
        connectEndNs = nowNsProvider()
    }

    fun onSecureConnectStart() {
        if (tlsStartNs < 0) tlsStartNs = nowNsProvider()
    }

    fun onSecureConnectEnd() {
        tlsEndNs = nowNsProvider()
    }

    fun onRequestHeadersStart() {
        if (requestHeadersStartNs < 0) requestHeadersStartNs = nowNsProvider()
    }

    fun onResponseHeadersStart() {
        if (responseHeadersStartNs < 0) responseHeadersStartNs = nowNsProvider()
    }

    fun snapshot(): NetworkCallMetricsSnapshot {
        val totalNs = durationNs(startNs = callStartNs, endNs = callEndNs)
        val dnsNs = durationNs(startNs = dnsStartNs, endNs = dnsEndNs)
        val connectNs = durationNs(startNs = connectStartNs, endNs = connectEndNs)
        val tlsNs = durationNs(startNs = tlsStartNs, endNs = tlsEndNs)
        val ttfbNs = durationNs(startNs = requestHeadersStartNs, endNs = responseHeadersStartNs)

        return NetworkCallMetricsSnapshot(
            dnsMs = dnsNs?.let(::nsToMs),
            connectMs = connectNs?.let(::nsToMs),
            tlsMs = tlsNs?.let(::nsToMs),
            ttfbMs = ttfbNs?.let(::nsToMs),
            totalMs = nsToMs(totalNs ?: 0L),
        )
    }

    private fun durationNs(
        startNs: Long,
        endNs: Long,
    ): Long? {
        if (startNs < 0 || endNs < 0 || endNs < startNs) return null
        return endNs - startNs
    }

    private fun nsToMs(ns: Long): Long = TimeUnit.NANOSECONDS.toMillis(ns)
}

@Singleton
public class NetworkTelemetryEventListenerFactory
    @Inject
    constructor(
        loggerFactory: LoggerFactory,
    ) : EventListener.Factory {
        private val logger = loggerFactory.get("OkHttpNetworkTelemetry")

        override fun create(call: Call): EventListener = NetworkTelemetryEventListener(call, logger)

        private class NetworkTelemetryEventListener(
            private val call: Call,
            private val logger: com.jabook.app.jabook.compose.core.logger.Logger,
        ) : EventListener() {
            private val tracker = NetworkCallMetricsTracker()

            override fun callStart(call: Call) {
                tracker.onCallStart()
            }

            override fun dnsStart(
                call: Call,
                domainName: String,
            ) {
                tracker.onDnsStart()
            }

            override fun dnsEnd(
                call: Call,
                domainName: String,
                inetAddressList: List<java.net.InetAddress>,
            ) {
                tracker.onDnsEnd()
            }

            override fun connectStart(
                call: Call,
                inetSocketAddress: java.net.InetSocketAddress,
                proxy: java.net.Proxy,
            ) {
                tracker.onConnectStart()
            }

            override fun secureConnectStart(call: Call) {
                tracker.onSecureConnectStart()
            }

            override fun secureConnectEnd(
                call: Call,
                handshake: okhttp3.Handshake?,
            ) {
                tracker.onSecureConnectEnd()
            }

            override fun connectEnd(
                call: Call,
                inetSocketAddress: java.net.InetSocketAddress,
                proxy: java.net.Proxy,
                protocol: okhttp3.Protocol?,
            ) {
                tracker.onConnectEnd()
            }

            override fun requestHeadersStart(call: Call) {
                tracker.onRequestHeadersStart()
            }

            override fun responseHeadersStart(call: Call) {
                tracker.onResponseHeadersStart()
            }

            override fun callEnd(call: Call) {
                tracker.onCallEnd()
                val snapshot = tracker.snapshot()
                logger.d {
                    val requestUrl =
                        this.call
                            .request()
                            .url
                    val host = requestUrl.host
                    val path = requestUrl.encodedPath
                    "Network metrics host=$host path=$path total=${snapshot.totalMs}ms " +
                        "dns=${snapshot.dnsMs ?: -1}ms connect=${snapshot.connectMs ?: -1}ms " +
                        "tls=${snapshot.tlsMs ?: -1}ms ttfb=${snapshot.ttfbMs ?: -1}ms"
                }
            }

            override fun callFailed(
                call: Call,
                ioe: java.io.IOException,
            ) {
                tracker.onCallEnd()
                val snapshot = tracker.snapshot()
                val requestUrl =
                    this.call
                        .request()
                        .url
                val host = requestUrl.host
                val path = requestUrl.encodedPath
                logger.w(ioe) {
                    "Network call failed host=$host path=$path total=${snapshot.totalMs}ms " +
                        "dns=${snapshot.dnsMs ?: -1}ms connect=${snapshot.connectMs ?: -1}ms " +
                        "tls=${snapshot.tlsMs ?: -1}ms ttfb=${snapshot.ttfbMs ?: -1}ms"
                }
                CrashDiagnostics.reportNonFatal(
                    tag = "network_call_failed",
                    throwable = ioe,
                    attributes =
                        mapOf(
                            "host" to host,
                            "path" to path,
                            "dns_ms" to (snapshot.dnsMs?.toString() ?: "n/a"),
                            "connect_ms" to (snapshot.connectMs?.toString() ?: "n/a"),
                            "tls_ms" to (snapshot.tlsMs?.toString() ?: "n/a"),
                            "ttfb_ms" to (snapshot.ttfbMs?.toString() ?: "n/a"),
                            "total_ms" to snapshot.totalMs.toString(),
                        ),
                )
            }
        }
    }
