package com.jabook.app.jabook.handlers

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class PlayerLifecycleMethodHandler(
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "com.jabook.app.jabook/player_lifecycle")

    @Volatile
    var isPlayerInitializing = false
        private set

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "setPlayerInitializing" -> {
                val isInitializing = call.argument<Boolean>("isInitializing") ?: false
                val oldValue = isPlayerInitializing
                isPlayerInitializing = isInitializing
                android.util.Log.i(
                    "PlayerLifecycle",
                    "Player initialization state changed: $oldValue -> $isInitializing",
                )
                result.success(true)
            }
            "isPlayerInitializing" -> {
                android.util.Log.d(
                    "PlayerLifecycle",
                    "isPlayerInitializing query: $isPlayerInitializing",
                )
                result.success(isPlayerInitializing)
            }
            else -> result.notImplemented()
        }
    }

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }
}
