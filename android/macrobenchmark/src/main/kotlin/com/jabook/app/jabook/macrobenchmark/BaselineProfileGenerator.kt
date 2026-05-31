package com.jabook.app.jabook.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    companion object {
        private const val TARGET_PACKAGE = "com.jabook.app.jabook.dev"
    }

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // Wait for the main UI to load
            device.waitForIdle()

            // Wait for content to appear (library/catalog screen)
            device.wait(
                Until.hasObject(By.res("com.jabook.app.jabook", "miniPlayer")),
                10_000,
            )

            // Navigate to player if mini player is visible
            val miniPlayer = device.findObject(By.res("com.jabook.app.jabook", "miniPlayer"))
            if (miniPlayer != null) {
                miniPlayer.click()
                device.waitForIdle()
                device.wait(
                    Until.hasObject(By.res("com.jabook.app.jabook", "playerScreen")),
                    5_000,
                )
                // Go back
                device.pressBack()
                device.waitForIdle()
            }

            // Navigate through sidebar items
            val sidebar = device.findObject(By.res("com.jabook.app.jabook", "sidebar"))
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
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }
}
