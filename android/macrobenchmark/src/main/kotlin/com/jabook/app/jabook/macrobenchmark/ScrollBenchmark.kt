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

package com.jabook.app.jabook.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val targetPackage: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    @Test
    fun noCompilation() = scroll(CompilationMode.None())

    @Test
    fun defaultCompilation() = scroll(CompilationMode.DEFAULT)

    @Test
    fun full() = scroll(CompilationMode.Full())

    private fun scroll(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = null,
            iterations = 5,
        ) {
            uiAutomator {
                startApp(targetPackage)
                device.waitForIdle()

                // Scroll the library content
                val scrollable =
                    device.findObject { isScrollable } ?: return@measureRepeated
                repeat(3) {
                    scrollable.fling(Direction.DOWN)
                    device.waitForIdle()
                }
                repeat(3) {
                    scrollable.fling(Direction.UP)
                    device.waitForIdle()
                }
            }
        }
    }
}
