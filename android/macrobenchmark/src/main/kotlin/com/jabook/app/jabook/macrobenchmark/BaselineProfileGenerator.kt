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

import android.util.Log
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val targetPackage: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    @Test
    fun generateBaselineProfile() {
        Log.d("BaselineProfile", "Generating profile for package: $targetPackage")
        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // Wait for the main UI to load
            device.waitForIdle()

            // Wait for content to appear (library/catalog screen)
            device.wait(
                Until.hasObject(By.res(targetPackage, "miniPlayer")),
                10_000,
            )

            // Navigate to player if mini player is visible
            val miniPlayer = device.findObject(By.res(targetPackage, "miniPlayer"))
            if (miniPlayer != null) {
                miniPlayer.click()
                device.waitForIdle()
                device.wait(
                    Until.hasObject(By.res(targetPackage, "playerScreen")),
                    5_000,
                )
                // Go back
                device.pressBack()
                device.waitForIdle()
            }

            // Navigate through sidebar items
            val sidebar = device.findObject(By.res(targetPackage, "sidebar"))
            if (sidebar != null) {
                // Click on different navigation items
                val navItems = sidebar.children
                for (i in 0 until minOf(navItems.size, 3)) {
                    navItems[i].click()
                    device.waitForIdle()
                    device.wait(Until.hasObject(By.hasDescendant(By.clickable(true))), 3_000)
                }
            }

            // Return to home
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    @Test
    fun startupCompilationBaselineProfile() {
        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }
}
