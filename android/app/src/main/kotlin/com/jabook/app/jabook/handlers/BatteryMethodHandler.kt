package com.jabook.app.jabook.handlers

import android.content.Context
import android.os.BatteryManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class BatteryMethodHandler(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "com.jabook.app.jabook/battery")

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "getBatteryLevel" -> {
                try {
                    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    result.success(batteryLevel)
                } catch (e: Exception) {
                    android.util.Log.e("BatteryMethodHandler", "Failed to get battery level: ${e.message}")
                    result.error("BATTERY_ERROR", "Failed to get battery level: ${e.message}", null)
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
