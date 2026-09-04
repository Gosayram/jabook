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
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps debounced widget update jobs bounded and ensures proper lifecycle cleanup.
 */
internal class WidgetUpdateJobRegistry {
    private val jobsByWidgetId = ConcurrentHashMap<Int, Job>()

    internal fun replace(
        widgetId: Int,
        newJob: Job,
    ) {
        jobsByWidgetId.put(widgetId, newJob)?.cancel()

        newJob.invokeOnCompletion {
            // Remove only if still the current job for this widget (prevents stale completion
            // from evicting a newer job that was registered while this one was running).
            jobsByWidgetId.remove(widgetId, newJob)
        }
    }

    internal fun cancelForIds(widgetIds: IntArray) {
        widgetIds.forEach { widgetId ->
            jobsByWidgetId.remove(widgetId)?.cancel()
        }
    }

    internal fun cancelAll() {
        val snapshot = jobsByWidgetId.values.toList()
        jobsByWidgetId.clear()
        snapshot.forEach { it.cancel() }
    }

    internal fun size(): Int = jobsByWidgetId.size
}
