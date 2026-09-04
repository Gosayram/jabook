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

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // Wait for the main UI to load
            device.waitForIdle()

            // The mini player is optional when a fresh install has no book yet.
            val miniPlayer = device.findObject(By.res(targetPackage, "miniPlayer"))
            if (miniPlayer != null) {
                miniPlayer.click()
                device.waitForIdle()
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
                }
            }

            // Return to home
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }

}
