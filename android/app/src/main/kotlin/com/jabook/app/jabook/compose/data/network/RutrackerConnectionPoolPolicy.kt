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

import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

/** Bounds idle RuTracker sockets to reduce background battery and memory use on mobile. */
internal object RutrackerConnectionPoolPolicy {
    internal data class Configuration(
        val maxIdleConnections: Int,
        val keepAliveDuration: Long,
        val keepAliveUnit: TimeUnit,
    )

    internal val configuration =
        Configuration(
            maxIdleConnections = 3,
            keepAliveDuration = 2L,
            keepAliveUnit = TimeUnit.MINUTES,
        )

    fun create(): ConnectionPool =
        ConnectionPool(
            maxIdleConnections = configuration.maxIdleConnections,
            keepAliveDuration = configuration.keepAliveDuration,
            timeUnit = configuration.keepAliveUnit,
        )
}
