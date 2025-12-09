package com.jabook.app.jabook.handlers

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class NotificationMethodHandler(
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "com.jabook.app.jabook/notification")

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "handleNotificationClick" -> {
                // This will be called from Flutter when notification is clicked
                // The actual navigation is handled in Flutter via GoRouter
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    fun invokeMethod(
        method: String,
        arguments: Any?,
    ) {
        channel.invokeMethod(method, arguments)
    }

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }
}
