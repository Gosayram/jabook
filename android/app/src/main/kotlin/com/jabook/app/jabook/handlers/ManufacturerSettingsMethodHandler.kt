package com.jabook.app.jabook.handlers

import android.content.Context
import com.jabook.app.jabook.ManufacturerSettingsHelper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class ManufacturerSettingsMethodHandler(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "manufacturer_settings_channel")
    private val packageName: String = context.packageName

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "openAutostartSettings" -> {
                try {
                    val success = ManufacturerSettingsHelper.openAutostartSettings(context, packageName)
                    result.success(success)
                } catch (e: Exception) {
                    result.error("OPEN_AUTOSTART_ERROR", "Failed to open autostart settings: ${e.message}", null)
                }
            }
            "openBatteryOptimizationSettings" -> {
                try {
                    val success = ManufacturerSettingsHelper.openBatteryOptimizationSettings(context, packageName)
                    result.success(success)
                } catch (e: Exception) {
                    result.error("OPEN_BATTERY_ERROR", "Failed to open battery optimization settings: ${e.message}", null)
                }
            }
            "openBackgroundRestrictionsSettings" -> {
                try {
                    val success = ManufacturerSettingsHelper.openBackgroundRestrictionsSettings(context, packageName)
                    result.success(success)
                } catch (e: Exception) {
                    result.error("OPEN_BACKGROUND_ERROR", "Failed to open background restrictions settings: ${e.message}", null)
                }
            }
            "checkAutostartEnabled" -> {
                try {
                    val enabled = ManufacturerSettingsHelper.isAutostartEnabled(context, packageName)
                    result.success(enabled)
                } catch (e: Exception) {
                    result.error("CHECK_AUTOSTART_ERROR", "Failed to check autostart status: ${e.message}", null)
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
}
