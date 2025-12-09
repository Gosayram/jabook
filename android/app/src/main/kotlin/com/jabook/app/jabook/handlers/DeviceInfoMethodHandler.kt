package com.jabook.app.jabook.handlers

import android.content.Context
import android.os.Build
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class DeviceInfoMethodHandler(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "device_info_channel")

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "getAppStandbyBucket" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                        val bucket = usageStatsManager.appStandbyBucket
                        result.success(bucket)
                    } catch (e: Exception) {
                        result.error("GET_STANDBY_BUCKET_ERROR", "Failed to get App Standby Bucket: ${e.message}", null)
                    }
                } else {
                    result.error("UNSUPPORTED", "App Standby Bucket requires Android 9.0+ (API 28+)", null)
                }
            }
            "getRomVersion" -> {
                try {
                    val romVersion = getRomVersion()
                    result.success(romVersion)
                } catch (e: Exception) {
                    result.error("GET_ROM_VERSION_ERROR", "Failed to get ROM version: ${e.message}", null)
                }
            }
            "getFirmwareVersion" -> {
                try {
                    val firmwareVersion = getFirmwareVersion()
                    result.success(firmwareVersion)
                } catch (e: Exception) {
                    result.error("GET_FIRMWARE_VERSION_ERROR", "Failed to get firmware version: ${e.message}", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }

    /**
     * Gets the ROM version by reading system properties.
     */
    private fun getRomVersion(): String? =
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()

            when {
                manufacturer.contains("xiaomi") ||
                    brand.contains("xiaomi") ||
                    brand.contains("redmi") ||
                    brand.contains("poco") -> {
                    val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
                    val miuiCode = getSystemProperty("ro.miui.ui.version.code")
                    when {
                        miuiVersion != null -> "MIUI $miuiVersion"
                        miuiCode != null -> "MIUI $miuiCode"
                        else -> null
                    }
                }
                manufacturer.contains("huawei") ||
                    brand.contains("huawei") ||
                    brand.contains("honor") -> {
                    val emuiVersion = getSystemProperty("ro.build.version.emui")
                    emuiVersion?.let { "EMUI $it" } ?: "EMUI"
                }
                manufacturer.contains("oppo") ||
                    brand.contains("oppo") ||
                    manufacturer.contains("realme") ||
                    brand.contains("realme") ||
                    manufacturer.contains("oneplus") ||
                    brand.contains("oneplus") -> {
                    val colorOsVersion = getSystemProperty("ro.build.version.opporom")
                    colorOsVersion?.let { "ColorOS $it" } ?: null
                }
                manufacturer.contains("samsung") || brand.contains("samsung") -> {
                    val oneUiVersion =
                        getSystemProperty("ro.build.version.oneui")
                            ?: getSystemProperty("ro.build.version.sem")

                    if (oneUiVersion != null) {
                        val isValidVersion =
                            oneUiVersion.contains(".") ||
                                (oneUiVersion.length <= 3 && oneUiVersion.toIntOrNull() != null && oneUiVersion.toInt() < 100)

                        if (isValidVersion) {
                            val majorVersion = oneUiVersion.split(".").firstOrNull()?.toIntOrNull()
                            if (majorVersion != null && majorVersion > 0 && majorVersion < 20) {
                                "One UI $majorVersion"
                            } else {
                                getOneUIVersionFromAndroidApi()
                            }
                        } else {
                            getOneUIVersionFromAndroidApi()
                        }
                    } else {
                        getOneUIVersionFromAndroidApi()
                    }
                }
                manufacturer.contains("vivo") || brand.contains("vivo") -> {
                    val funtouchVersion = getSystemProperty("ro.vivo.os.version")
                    funtouchVersion?.let { "FuntouchOS $it" } ?: null
                }
                manufacturer.contains("meizu") || brand.contains("meizu") -> {
                    val flymeVersion = getSystemProperty("ro.build.display.id")
                    flymeVersion?.let { "Flyme $it" } ?: null
                }
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.w("DeviceInfoHandler", "Failed to get ROM version: ${e.message}")
            null
        }

    private fun getOneUIVersionFromAndroidApi(): String {
        val androidApi = Build.VERSION.SDK_INT
        val oneUiVersion =
            when (androidApi) {
                36 -> 8
                35 -> 8
                34 -> 7
                33 -> 6
                32, 31 -> 5
                30 -> 4
                29 -> 3
                28 -> 2
                27 -> 1
                else -> null
            }
        return oneUiVersion?.let { "One UI $it" } ?: "One UI"
    }

    private fun getFirmwareVersion(): String? {
        return try {
            val displayId = getSystemProperty("ro.build.display.id")
            if (!displayId.isNullOrEmpty() && displayId != "unknown") {
                val parts = displayId.split(".")
                if (parts.isNotEmpty()) {
                    val buildNumber = parts.lastOrNull()
                    if (!buildNumber.isNullOrEmpty() && buildNumber.length >= 10) {
                        return buildNumber
                    }
                }
                return displayId
            }

            val incremental = getSystemProperty("ro.build.version.incremental")
            if (!incremental.isNullOrEmpty() && incremental != "unknown") {
                return incremental
            }

            val buildId = Build.ID
            if (buildId.isNotEmpty() && buildId != "unknown") {
                return buildId
            }

            null
        } catch (e: Exception) {
            android.util.Log.w("DeviceInfoHandler", "Failed to get firmware version: ${e.message}")
            null
        }
    }

    private fun getSystemProperty(key: String): String? =
        try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader =
                java.io.BufferedReader(
                    java.io.InputStreamReader(process.inputStream),
                )
            val value = reader.readLine()
            reader.close()
            process.waitFor()
            if (value.isNullOrEmpty()) null else value.trim()
        } catch (e: Exception) {
            null
        }
}
