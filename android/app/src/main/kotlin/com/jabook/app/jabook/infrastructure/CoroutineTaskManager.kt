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

package com.jabook.app.jabook.infrastructure

import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger

public enum class TaskPriority {
    LIGHT,
    MEDIUM,
    HEAVY,
}

public object CoroutineTaskManager {
    private const val TAG = "CoroutineTaskManager"

    // Dispatchers.IO.limitedParallelism handles thread pooling natively — no raw Executors needed.
    private val heavyDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val mediumDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)
    private val lightDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    public val mediaItemDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val activeHeavyTasks = AtomicInteger(0)
    private val activeMediumTasks = AtomicInteger(0)
    private val activeLightTasks = AtomicInteger(0)

    @Volatile
    private var paused = false

    public fun getDispatcher(priority: TaskPriority): CoroutineDispatcher =
        when (priority) {
            TaskPriority.HEAVY -> heavyDispatcher
            TaskPriority.MEDIUM -> mediumDispatcher
            TaskPriority.LIGHT -> lightDispatcher
        }

    internal fun incrementActiveTasks(priority: TaskPriority) {
        when (priority) {
            TaskPriority.HEAVY -> activeHeavyTasks.incrementAndGet()
            TaskPriority.MEDIUM -> activeMediumTasks.incrementAndGet()
            TaskPriority.LIGHT -> activeLightTasks.incrementAndGet()
        }
    }

    internal fun decrementActiveTasks(priority: TaskPriority) {
        when (priority) {
            TaskPriority.HEAVY -> activeHeavyTasks.decrementAndGet()
            TaskPriority.MEDIUM -> activeMediumTasks.decrementAndGet()
            TaskPriority.LIGHT -> activeLightTasks.decrementAndGet()
        }
    }

    public fun getStatistics(): Map<String, Any> =
        mapOf(
            "active_heavy" to activeHeavyTasks.get(),
            "active_medium" to activeMediumTasks.get(),
            "active_light" to activeLightTasks.get(),
            "paused" to paused,
        )

    public fun pauseNonCritical() {
        paused = true
        LogUtils.i(TAG, "Paused non-critical tasks")
    }

    public fun resume() {
        paused = false
        LogUtils.i(TAG, "Resumed non-critical tasks")
    }

    public fun isPaused(): Boolean = paused

    public fun shutdown() {
        LogUtils.i(TAG, "CoroutineTaskManager shutdown (Dispatchers.IO manages threads)")
    }
}
