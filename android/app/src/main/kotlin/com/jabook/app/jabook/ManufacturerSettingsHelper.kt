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

package com.jabook.app.jabook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helper class for opening manufacturer-specific settings on Android devices.
 *
 * Different Android manufacturers (Xiaomi, Huawei, Oppo, etc.) have different
 * paths to autostart, battery optimization, and background restriction settings.
 * This class provides methods to open these settings with proper fallbacks.
 */
object ManufacturerSettingsHelper {
    private const val TAG = "ManufacturerSettingsHelper"

    /**
     * Checks if an Intent is available on the device.
     *
     * This method verifies that the Intent can be resolved and that there's
     * at least one activity that can handle it. This prevents crashes when
     * trying to open manufacturer-specific settings that may not be available
     * on all devices or ROM versions.
     *
     * @param context The context to use for checking
     * @param intent The Intent to check
     * @return true if the Intent is available, false otherwise
     */
    private fun isIntentAvailable(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            // Use MATCH_DEFAULT_ONLY to only check for activities that can be
            // started by default (not requiring special permissions)
            // Also use MATCH_ALL flag to ensure we check all possible handlers
            val resolveInfo =
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL,
                )
            // Only return true if we found at least one activity AND the activity
            // is exported or from a system package (safer for OEM settings)
            resolveInfo.isNotEmpty() &&
                resolveInfo.any { info ->
                    // Check if activity is exported or from system package
                    // This ensures we only use publicly accessible settings
                    info.activityInfo.exported ||
                        info.activityInfo.packageName.startsWith("com.android") ||
                        info.activityInfo.packageName.startsWith("com.miui") ||
                        info.activityInfo.packageName.startsWith("com.huawei") ||
                        info.activityInfo.packageName.startsWith("com.coloros") ||
                        info.activityInfo.packageName.startsWith("com.oppo") ||
                        info.activityInfo.packageName.startsWith("com.vivo") ||
                        info.activityInfo.packageName.startsWith("com.meizu") ||
                        info.activityInfo.packageName.startsWith("com.samsung")
                }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error checking Intent availability: ${e.message}")
            false
        }

    /**
     * Opens autostart settings for the app.
     *
     * Tries manufacturer-specific Intent's first, then falls back to standard Android settings.
     *
     * @param context The context to use for opening settings
     * @param packageName The package name of the app
     * @return true if settings were opened successfully, false otherwise
     */
    fun openAutostartSettings(
        context: Context,
        packageName: String,
    ): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        // Try manufacturer-specific Intent's
        val intents = mutableListOf<Intent>()

        // Xiaomi/Redmi/Poco (MIUI)
        if (manufacturer.contains("xiaomi") ||
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco")
        ) {
            // Primary Intent for MIUI autostart
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        )
                },
            )
            // Alternative Intent for MIUI
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.MainActivity",
                        )
                    putExtra("extra_pkgname", packageName)
                },
            )
        }

        // Huawei/Honor (EMUI/HarmonyOS)
        if (manufacturer.contains("huawei") ||
            brand.contains("huawei") ||
            brand.contains("honor")
        ) {
            // Primary Intent for EMUI autostart
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        )
                },
            )
            // Alternative Intent for EMUI
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                        )
                },
            )
        }

        // Oppo (ColorOS)
        if (manufacturer.contains("oppo") || brand.contains("oppo")) {
            // Primary Intent for ColorOS autostart
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                        )
                },
            )
            // Alternative Intent for ColorOS
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppControlActivity",
                        )
                },
            )
        }

        // OnePlus (OxygenOS - based on ColorOS)
        if (manufacturer.contains("oneplus") || brand.contains("oneplus")) {
            // Similar to Oppo ColorOS
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                        )
                },
            )
        }

        // Realme (RealmeUI - based on ColorOS)
        if (manufacturer.contains("realme") || brand.contains("realme")) {
            // Similar to Oppo ColorOS
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                        )
                },
            )
        }

        // Vivo (FuntouchOS/OriginOS)
        if (manufacturer.contains("vivo") || brand.contains("vivo")) {
            // Primary Intent for FuntouchOS autostart
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                        )
                },
            )
            // Alternative Intent for Vivo
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                        )
                },
            )
        }

        // Meizu (Flyme)
        if (manufacturer.contains("meizu") || brand.contains("meizu")) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.meizu.safe",
                            "com.meizu.safe.permission.SmartBGAppStartActivity",
                        )
                },
            )
        }

        // Try manufacturer-specific Intent's first
        for (intent in intents) {
            if (isIntentAvailable(context, intent)) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    // Continue to next Intent
                }
            }
        }

        // Fallback to standard Android app settings
        return openAppDetailsSettings(context, packageName)
    }

    /**
     * Opens battery optimization settings for the app.
     *
     * Tries manufacturer-specific Intent's first, then falls back to standard Android settings.
     *
     * @param context The context to use for opening settings
     * @param packageName The package name of the app
     * @return true if settings were opened successfully, false otherwise
     */
    fun openBatteryOptimizationSettings(
        context: Context,
        packageName: String,
    ): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        // Try manufacturer-specific Intent's
        val intents = mutableListOf<Intent>()

        // Xiaomi/Redmi/Poco (MIUI)
        if (manufacturer.contains("xiaomi") ||
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco")
        ) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                        )
                },
            )
        }

        // Huawei/Honor (EMUI/HarmonyOS)
        if (manufacturer.contains("huawei") ||
            brand.contains("huawei") ||
            brand.contains("honor")
        ) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity",
                        )
                },
            )
            // Alternative Intent for Huawei
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.power.ui.HwPowerManagerActivity",
                        )
                },
            )
        }

        // Oppo (ColorOS)
        if (manufacturer.contains("oppo") || brand.contains("oppo")) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.oppoguardelf.activity.PowerConsumptionActivity",
                        )
                },
            )
            // Alternative Intent for Oppo
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.battery.BatteryOptimizationActivity",
                        )
                },
            )
        }

        // OnePlus, Realme (similar to Oppo)
        if (manufacturer.contains("oneplus") ||
            brand.contains("oneplus") ||
            manufacturer.contains("realme") ||
            brand.contains("realme")
        ) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.oppoguardelf.activity.PowerConsumptionActivity",
                        )
                },
            )
        }

        // Vivo (FuntouchOS/OriginOS)
        if (manufacturer.contains("vivo") || brand.contains("vivo")) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.vivo.abe",
                            "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
                        )
                },
            )
        }

        // Meizu (Flyme)
        if (manufacturer.contains("meizu") || brand.contains("meizu")) {
            intents.add(
                Intent().apply {
                    component =
                        android.content.ComponentName(
                            "com.meizu.safe",
                            "com.meizu.safe.powerui.PowerAppPermissionEditorActivity",
                        )
                },
            )
        }

        // Try manufacturer-specific Intent's first
        for (intent in intents) {
            if (isIntentAvailable(context, intent)) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    // Continue to next Intent
                }
            }
        }

        // Fallback to standard Android battery optimization settings (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (isIntentAvailable(context, intent)) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                // Continue to app details fallback
            }
        }

        // Final fallback to app details settings
        return openAppDetailsSettings(context, packageName)
    }

    /**
     * Opens background restrictions settings for the app.
     *
     * This is typically the same as battery optimization settings on most devices.
     *
     * @param context The context to use for opening settings
     * @param packageName The package name of the app
     * @return true if settings were opened successfully, false otherwise
     */
    fun openBackgroundRestrictionsSettings(
        context: Context,
        packageName: String,
    ): Boolean {
        // For most manufacturers, background restrictions are managed through
        // battery optimization settings, so we use the same method
        return openBatteryOptimizationSettings(context, packageName)
    }

    /**
     * Opens standard Android app details settings as a fallback.
     *
     * @param context The context to use for opening settings
     * @param packageName The package name of the app
     * @return true if settings were opened successfully, false otherwise
     */
    private fun openAppDetailsSettings(
        context: Context,
        packageName: String,
    ): Boolean =
        try {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            if (isIntentAvailable(context, intent)) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

    /**
     * Checks if autostart is enabled for the app (if possible to determine).
     *
     * Note: This is not always possible on all devices/manufacturers.
     *
     * @param context The context to use
     * @param packageName The package name of the app
     * @return true if autostart is enabled, false if disabled or cannot be determined
     */
    fun isAutostartEnabled(
        context: Context,
        packageName: String,
    ): Boolean {
        // Most manufacturers don't provide a public API to check autostart status
        // This would require root access or manufacturer-specific APIs
        // For now, we return false (unknown) and let the user check manually
        return false
    }
}
