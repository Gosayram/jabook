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

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ColdStartupBenchmark : AbstractStartupBenchmark(StartupMode.COLD)

@RunWith(JUnit4::class)
class WarmStartupBenchmark : AbstractStartupBenchmark(StartupMode.WARM)

abstract class AbstractStartupBenchmark(private val startupMode: StartupMode) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val targetPackage: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupPartialCompilation() =
        startup(
            CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Disable,
                warmupIterations = 3,
            ),
        )

    @Test
    fun startupPartialWithBaselineProfiles() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = startupMode,
        ) {
            uiAutomator {
                startApp(targetPackage)
            }
        }
}
