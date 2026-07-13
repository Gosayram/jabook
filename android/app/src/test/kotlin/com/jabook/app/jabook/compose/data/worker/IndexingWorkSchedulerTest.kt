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

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IndexingWorkSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: IndexingWorkScheduler
    private val requestCaptor = argumentCaptor<OneTimeWorkRequest>()

    @Before
    fun setUp() {
        workManager = mock()
        whenever(workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())).thenReturn(mock<Operation>())
        scheduler = IndexingWorkScheduler(workManager)
    }

    @Test
    fun `enqueue uses expedited unique work with a connected network constraint`() {
        scheduler.enqueue()

        verify(workManager).enqueueUniqueWork(
            eq(IndexingWorker.WORK_NAME_ONE_TIME),
            eq(ExistingWorkPolicy.KEEP),
            requestCaptor.capture(),
        )
        val workSpec = requestCaptor.firstValue.workSpec
        assertTrue(workSpec.expedited)
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertTrue(workSpec.input.getBoolean(IndexingWorker.KEY_PRELOAD_COVERS, false))
    }
}
