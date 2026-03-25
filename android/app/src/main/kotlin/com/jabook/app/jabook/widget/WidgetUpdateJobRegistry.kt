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

/**
 * Keeps debounced widget update jobs bounded and ensures proper lifecycle cleanup.
 */
internal class WidgetUpdateJobRegistry {
    private val lock = Any()
    private val jobsByWidgetId = mutableMapOf<Int, Job>()

    internal fun replace(
        widgetId: Int,
        newJob: Job,
    ) {
        synchronized(lock) {
            jobsByWidgetId.remove(widgetId)?.cancel()
            jobsByWidgetId[widgetId] = newJob
        }

        newJob.invokeOnCompletion {
            synchronized(lock) {
                if (jobsByWidgetId[widgetId] == newJob) {
                    jobsByWidgetId.remove(widgetId)
                }
            }
        }
    }

    internal fun cancelForIds(widgetIds: IntArray) {
        synchronized(lock) {
            widgetIds.forEach { widgetId ->
                jobsByWidgetId.remove(widgetId)?.cancel()
            }
        }
    }

    internal fun cancelAll() {
        val jobsToCancel: List<Job> =
            synchronized(lock) {
                val snapshot = jobsByWidgetId.values.toList()
                jobsByWidgetId.clear()
                snapshot
            }

        jobsToCancel.forEach { job ->
            job.cancel()
        }
    }

    internal fun size(): Int = synchronized(lock) { jobsByWidgetId.size }
}
