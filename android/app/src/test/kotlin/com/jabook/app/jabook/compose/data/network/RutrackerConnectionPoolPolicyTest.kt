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
import org.junit.Test
import java.util.concurrent.TimeUnit

class RutrackerConnectionPoolPolicyTest {
    @Test
    fun `keeps only a small short-lived idle pool for rutracker clients`() {
        val configuration = RutrackerConnectionPoolPolicy.configuration

        assertEquals(3, configuration.maxIdleConnections)
        assertEquals(2L, configuration.keepAliveDuration)
        assertEquals(TimeUnit.MINUTES, configuration.keepAliveUnit)
    }
}
