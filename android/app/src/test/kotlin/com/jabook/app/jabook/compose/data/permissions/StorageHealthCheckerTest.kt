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

package com.jabook.app.jabook.compose.data.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageHealthCheckerTest {
    @Test
    fun `forced low storage simulation returns unhealthy result`() {
        val checker =
            StorageHealthChecker(
                minAvailableBytes = 50L * 1024 * 1024,
                forceLowStorageProvider = { true },
            )

        val result = checker.check("/tmp")

        assertFalse(result.isHealthy)
        assertNotNull(result.warningMessage)
        assertTrue(result.warningMessage?.contains("debug simulation", ignoreCase = true) == true)
    }
}
