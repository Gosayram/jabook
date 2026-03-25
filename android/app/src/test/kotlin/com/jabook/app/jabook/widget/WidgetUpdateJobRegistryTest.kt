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

package com.jabook.app.jabook.widget

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUpdateJobRegistryTest {
    @Test
    fun `replace cancels previous job for same widget`() {
        val registry = WidgetUpdateJobRegistry()
        val first = Job()
        val second = Job()

        registry.replace(widgetId = 1, newJob = first)
        registry.replace(widgetId = 1, newJob = second)

        assertTrue(first.isCancelled)
        assertFalse(second.isCancelled)
        assertEquals(1, registry.size())
    }

    @Test
    fun `completed job is removed from registry`() {
        val registry = WidgetUpdateJobRegistry()
        val job = Job()

        registry.replace(widgetId = 2, newJob = job)
        assertEquals(1, registry.size())

        job.cancel()

        assertEquals(0, registry.size())
    }

    @Test
    fun `cancelForIds cancels only selected jobs`() {
        val registry = WidgetUpdateJobRegistry()
        val first = Job()
        val second = Job()

        registry.replace(widgetId = 10, newJob = first)
        registry.replace(widgetId = 20, newJob = second)

        registry.cancelForIds(intArrayOf(10))

        assertTrue(first.isCancelled)
        assertFalse(second.isCancelled)
        assertEquals(1, registry.size())
    }

    @Test
    fun `cancelAll cancels every tracked job and clears registry`() {
        val registry = WidgetUpdateJobRegistry()
        val first = Job()
        val second = Job()

        registry.replace(widgetId = 10, newJob = first)
        registry.replace(widgetId = 20, newJob = second)

        registry.cancelAll()

        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertEquals(0, registry.size())
    }
}
