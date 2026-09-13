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

package com.jabook.app.jabook.compose.data.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CoverEnrichmentSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: CoverEnrichmentScheduler
    private val requestCaptor = argumentCaptor<OneTimeWorkRequest>()

    @Before
    fun setUp() {
        workManager = mock()
        whenever(workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())).thenReturn(mock<Operation>())
        scheduler = CoverEnrichmentScheduler(workManager)
    }

    @Test
    fun `enqueue uses unique KEEP work with connectivity and resource constraints`() {
        scheduler.enqueue()

        verify(workManager).enqueueUniqueWork(
            eq(CoverEnrichmentWorker.WORK_NAME),
            eq(ExistingWorkPolicy.KEEP),
            requestCaptor.capture(),
        )
        val constraints = requestCaptor.firstValue.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
        assertTrue(constraints.requiresStorageNotLow())
    }

    @Test
    fun `enqueue sets exponential backoff at 30 minutes`() {
        scheduler.enqueue()

        verify(workManager).enqueueUniqueWork(
            eq(CoverEnrichmentWorker.WORK_NAME),
            eq(ExistingWorkPolicy.KEEP),
            requestCaptor.capture(),
        )
        val workSpec = requestCaptor.firstValue.workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(30), workSpec.backoffDelayDuration)
    }
}
