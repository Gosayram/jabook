// Copyright 2025 Jabook Contributors
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

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task priority levels for coroutine tasks.
 */
enum class TaskPriority {
    /** Light tasks: caching, UI updates */
    LIGHT,

    /** Medium tasks: metadata downloads, MediaItem creation */
    MEDIUM,

    /** Heavy tasks: torrent parsing, library scanning */
    HEAVY,
}

/**
 * Centralized task manager for coroutines with priority-based execution.
 *
 * This manager provides:
 * - Priority-based dispatchers with limited parallelism
 * - Fixed thread pools (2-4 threads based on CPU cores)
 * - Task monitoring and statistics
 * - Energy efficiency support (pause/resume)
 */
object CoroutineTaskManager {
    private const val TAG = "CoroutineTaskManager"

    // Number of CPU cores (defaults to 2 if detection fails)
    private val cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    // Maximum number of concurrent threads (2-4 based on CPU)
    private val maxConcurrentThreads: Int =
        when {
            cpuCores <= 2 -> 2
            cpuCores <= 4 -> 3
            else -> 4
        }

    // Fixed thread pools for different priorities
    private val heavyExecutor =
        Executors.newFixedThreadPool(maxConcurrentThreads) { r ->
            Thread(r, "TaskManager-HEAVY").apply { isDaemon = true }
        }

    private val mediumExecutor =
        Executors.newFixedThreadPool(maxConcurrentThreads) { r ->
            Thread(r, "TaskManager-MEDIUM").apply { isDaemon = true }
        }

    private val lightExecutor =
        Executors.newFixedThreadPool(maxConcurrentThreads) { r ->
            Thread(r, "TaskManager-LIGHT").apply { isDaemon = true }
        }

    // Dispatchers for different priorities
    private val heavyDispatcher: CoroutineDispatcher = heavyExecutor.asCoroutineDispatcher()
    private val mediumDispatcher: CoroutineDispatcher = mediumExecutor.asCoroutineDispatcher()
    private val lightDispatcher: CoroutineDispatcher = lightExecutor.asCoroutineDispatcher()

    // Limited parallelism dispatchers (for MediaItem creation, etc.)
    val mediaItemDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    // Task counters for monitoring
    private val activeHeavyTasks = AtomicInteger(0)
    private val activeMediumTasks = AtomicInteger(0)
    private val activeLightTasks = AtomicInteger(0)

    // Pause state
    @Volatile
    private var paused = false

    /**
     * Get dispatcher for the given priority.
     *
     * @param priority Task priority
     * @return Coroutine dispatcher for the priority
     */
    fun getDispatcher(priority: TaskPriority): CoroutineDispatcher =
        when (priority) {
            TaskPriority.HEAVY -> heavyDispatcher
            TaskPriority.MEDIUM -> mediumDispatcher
            TaskPriority.LIGHT -> lightDispatcher
        }

    /**
     * Increment active task counter for monitoring.
     *
     * @param priority Task priority
     */
    internal fun incrementActiveTasks(priority: TaskPriority) {
        when (priority) {
            TaskPriority.HEAVY -> activeHeavyTasks.incrementAndGet()
            TaskPriority.MEDIUM -> activeMediumTasks.incrementAndGet()
            TaskPriority.LIGHT -> activeLightTasks.incrementAndGet()
        }
    }

    /**
     * Decrement active task counter for monitoring.
     *
     * @param priority Task priority
     */
    internal fun decrementActiveTasks(priority: TaskPriority) {
        when (priority) {
            TaskPriority.HEAVY -> activeHeavyTasks.decrementAndGet()
            TaskPriority.MEDIUM -> activeMediumTasks.decrementAndGet()
            TaskPriority.LIGHT -> activeLightTasks.decrementAndGet()
        }
    }

    /**
     * Get current task statistics.
     *
     * @return Map with statistics
     */
    fun getStatistics(): Map<String, Any> =
        mapOf(
            "active_heavy" to activeHeavyTasks.get(),
            "active_medium" to activeMediumTasks.get(),
            "active_light" to activeLightTasks.get(),
            "max_concurrent" to maxConcurrentThreads,
            "cpu_cores" to cpuCores,
            "paused" to paused,
        )

    /**
     * Pause non-critical tasks (LIGHT and MEDIUM).
     *
     * This is useful when the app goes to background or battery is low.
     */
    fun pauseNonCritical() {
        paused = true
        Log.i(TAG, "Paused non-critical tasks")
    }

    /**
     * Resume non-critical tasks.
     */
    fun resume() {
        paused = false
        Log.i(TAG, "Resumed non-critical tasks")
    }

    /**
     * Check if tasks are paused.
     *
     * @return True if paused
     */
    fun isPaused(): Boolean = paused

    /**
     * Shutdown all executors (for cleanup).
     */
    fun shutdown() {
        heavyExecutor.shutdown()
        mediumExecutor.shutdown()
        lightExecutor.shutdown()
        Log.i(TAG, "Shutdown all executors")
    }
}
