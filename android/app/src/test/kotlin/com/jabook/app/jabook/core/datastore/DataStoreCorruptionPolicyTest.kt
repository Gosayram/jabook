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

package com.jabook.app.jabook.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.emptyPreferences
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.crash.CrashDiagnosticsSink
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreCorruptionPolicyTest {
    private lateinit var previousSinkFactory: () -> CrashDiagnosticsSink
    private lateinit var sink: RecordingCrashSink

    @Before
    fun setUp() {
        sink = RecordingCrashSink()
        previousSinkFactory = CrashDiagnostics.sinkFactory
        CrashDiagnostics.isEnabledOverride = true
        CrashDiagnostics.sinkFactory = { sink }
    }

    @After
    fun tearDown() {
        CrashDiagnostics.isEnabledOverride = null
        CrashDiagnostics.sinkFactory = previousSinkFactory
    }

    @Test
    fun `preferences handler resets to empty preferences and reports telemetry`() =
        runTest {
            val handler = DataStoreCorruptionPolicy.preferencesHandler(storeName = "prefs_store")

            val recovered = handler.handleCorruption(CorruptionException("broken"))

            assertEquals(emptyPreferences(), recovered)
            assertTrue(sink.logs.any { it.contains("datastore_corruption_recovered") })
        }

    @Test
    fun `proto handler resets to default value and reports telemetry`() =
        runTest {
            val handler = DataStoreCorruptionPolicy.protoHandler(storeName = "proto_store", defaultValue = 42)

            val recovered = handler.handleCorruption(CorruptionException("broken"))

            assertEquals(42, recovered)
            assertTrue(sink.logs.any { it.contains("datastore_corruption_recovered") })
        }

    private class RecordingCrashSink : CrashDiagnosticsSink {
        val logs = mutableListOf<String>()

        override fun setCustomKey(
            key: String,
            value: String,
        ) = Unit

        override fun recordException(throwable: Throwable) = Unit

        override fun log(message: String) {
            logs += message
        }
    }
}
