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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkCallMetricsTrackerTest {
    @Test
    fun `snapshot calculates all durations in milliseconds`() {
        var nowNs = 0L
        val tracker = NetworkCallMetricsTracker(nowNsProvider = { nowNs })

        tracker.onCallStart() // 0ms
        nowNs = 1_000_000L
        tracker.onDnsStart() // 1ms
        nowNs = 3_000_000L
        tracker.onDnsEnd() // 3ms
        nowNs = 4_000_000L
        tracker.onConnectStart() // 4ms
        nowNs = 5_000_000L
        tracker.onSecureConnectStart() // 5ms
        nowNs = 8_000_000L
        tracker.onSecureConnectEnd() // 8ms
        nowNs = 9_000_000L
        tracker.onConnectEnd() // 9ms
        nowNs = 10_000_000L
        tracker.onRequestHeadersStart() // 10ms
        nowNs = 16_000_000L
        tracker.onResponseHeadersStart() // 16ms
        nowNs = 20_000_000L
        tracker.onCallEnd() // 20ms

        val snapshot = tracker.snapshot()
        assertEquals(20L, snapshot.totalMs)
        assertEquals(2L, snapshot.dnsMs)
        assertEquals(5L, snapshot.connectMs)
        assertEquals(3L, snapshot.tlsMs)
        assertEquals(6L, snapshot.ttfbMs)
    }

    @Test
    fun `snapshot keeps optional durations null when phase is absent`() {
        var nowNs = 0L
        val tracker = NetworkCallMetricsTracker(nowNsProvider = { nowNs })

        tracker.onCallStart()
        nowNs = 5_000_000L
        tracker.onCallEnd()

        val snapshot = tracker.snapshot()
        assertEquals(5L, snapshot.totalMs)
        assertNull(snapshot.dnsMs)
        assertNull(snapshot.connectMs)
        assertNull(snapshot.tlsMs)
        assertNull(snapshot.ttfbMs)
    }
}
