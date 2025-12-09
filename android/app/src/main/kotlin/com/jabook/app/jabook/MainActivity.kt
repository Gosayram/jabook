package com.jabook.app.jabook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.view.WindowCompat
import com.jabook.app.jabook.audio.AudioPlayerMethodHandler
import com.jabook.app.jabook.audio.PositionConstants
import com.jabook.app.jabook.audio.bridge.BridgeInitializer
import com.jabook.app.jabook.audio.bridge.EventChannelHandler
import com.jabook.app.jabook.download.DownloadServiceMethodHandler
import com.jabook.app.jabook.handlers.BatteryMethodHandler
import com.jabook.app.jabook.handlers.ContentUriMethodHandler
import com.jabook.app.jabook.handlers.CookieMethodHandler
import com.jabook.app.jabook.handlers.DeviceInfoMethodHandler
import com.jabook.app.jabook.handlers.DirectoryPickerMethodHandler
import com.jabook.app.jabook.handlers.ManufacturerSettingsMethodHandler
import com.jabook.app.jabook.handlers.NotificationMethodHandler
import com.jabook.app.jabook.handlers.PermissionMethodHandler
import com.jabook.app.jabook.handlers.PlayerLifecycleMethodHandler
import dagger.hilt.android.AndroidEntryPoint
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FlutterFragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable Edge-to-Edge for modern Android adaptation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        super.onCreate(savedInstanceState)
    }

    @Inject
    lateinit var eventChannelHandler: EventChannelHandler

    private var positionSaveReceiver: BroadcastReceiver? = null
    private var exitAppReceiver: BroadcastReceiver? = null

    // Need reference to check initialization state in receiver
    private var playerLifecycleHandler: PlayerLifecycleMethodHandler? = null

    companion object {
        // Store MethodChannel for AudioPlayerService (set when Flutter engine is configured)
        @Volatile
        private var audioPlayerMethodChannel: MethodChannel? = null

        /**
         * Gets the stored MethodChannel for AudioPlayerService.
         */
        fun getAudioPlayerMethodChannel(): MethodChannel? = audioPlayerMethodChannel

        /**
         * Sets the MethodChannel for AudioPlayerService.
         */
        fun setAudioPlayerMethodChannel(channel: MethodChannel?) {
            audioPlayerMethodChannel = channel
            android.util.Log.d("MainActivity", "AudioPlayerMethodChannel stored: ${channel != null}")
            // Try to set it in service if service already exists
            val service =
                com.jabook.app.jabook.audio.AudioPlayerService
                    .getInstance()
            service?.setMethodChannel(channel)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        // Initialize and register all MethodHandlers
        CookieMethodHandler(messenger)
        DirectoryPickerMethodHandler(this, messenger)
        PermissionMethodHandler(this, messenger)
        DeviceInfoMethodHandler(this, messenger)
        ManufacturerSettingsMethodHandler(this, messenger)
        BatteryMethodHandler(this, messenger)
        NotificationMethodHandler(messenger)
        ContentUriMethodHandler(this, messenger)

        // Save reference for receiver usage
        playerLifecycleHandler = PlayerLifecycleMethodHandler(messenger)

        // Register AudioPlayerChannel
        val audioPlayerChannel =
            MethodChannel(
                messenger,
                "com.jabook.app.jabook/audio_player",
            )
        audioPlayerChannel.setMethodCallHandler(
            AudioPlayerMethodHandler(this),
        )

        // Store MethodChannel for AudioPlayerService
        setAudioPlayerMethodChannel(audioPlayerChannel)

        // Initialize new FlutterBridge (parallel to old API for gradual migration)
        try {
            BridgeInitializer.initializeBridge(this, flutterEngine, eventChannelHandler)
            android.util.Log.i("MainActivity", "New FlutterBridge initialized for gradual migration")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize new FlutterBridge", e)
        }

        // Register BroadcastReceiver for saving position before unload
        registerPositionSaveReceiver(flutterEngine, audioPlayerChannel)

        // Register BroadcastReceiver for app exit (sleep timer)
        registerExitAppReceiver()

        // Register DownloadServiceChannel
        val downloadServiceChannel =
            MethodChannel(
                messenger,
                "com.jabook.app.jabook/download_service",
            )
        downloadServiceChannel.setMethodCallHandler(
            DownloadServiceMethodHandler(this),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle notification click - open player if requested
        if (intent.getBooleanExtra("open_player", false)) {
            val messenger = flutterEngine?.dartExecutor?.binaryMessenger
            if (messenger != null) {
                // Use the handlers package logic if possible, or just invoke directly
                val channel = MethodChannel(messenger, "com.jabook.app.jabook/notification")
                channel.invokeMethod("openPlayer", null)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save playback position when activity is paused
        try {
            val service =
                com.jabook.app.jabook.audio.AudioPlayerService
                    .getInstance()
            if (service != null) {
                android.util.Log.d("MainActivity", "Activity paused, triggering position save")
                service.saveCurrentPosition()
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to save position on pause", e)
        }
    }

    override fun onStop() {
        super.onStop()
        // Save playback position when activity is stopped
        try {
            val service =
                com.jabook.app.jabook.audio.AudioPlayerService
                    .getInstance()
            if (service != null) {
                android.util.Log.d("MainActivity", "Activity stopped, triggering position save")
                service.saveCurrentPosition()
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to save position on stop", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if we should open player from notification
        val intent = intent
        if (intent != null && intent.getBooleanExtra("open_player", false)) {
            intent.removeExtra("open_player")
            val messenger = flutterEngine?.dartExecutor?.binaryMessenger
            if (messenger != null) {
                val channel = MethodChannel(messenger, "com.jabook.app.jabook/notification")
                channel.invokeMethod("openPlayer", null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPositionSaveReceiver()
        unregisterExitAppReceiver()
        // Stop listening on handlers if kept
        playerLifecycleHandler?.stopListening()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // Delegating media buttons to MediaSession
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                android.util.Log.d("MainActivity", "Media button pressed (keyCode=$keyCode), delegating to MediaSession")
                return false
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                android.util.Log.d("MainActivity", "Media button released (keyCode=$keyCode), delegating to MediaSession")
                return false
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun registerPositionSaveReceiver(
        flutterEngine: FlutterEngine,
        @Suppress("UNUSED_PARAMETER") audioPlayerChannel: MethodChannel,
    ) {
        positionSaveReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == PositionConstants.ACTION_SAVE_POSITION_BEFORE_UNLOAD) {
                        val trackIndex = intent.getIntExtra(PositionConstants.EXTRA_TRACK_INDEX, -1)
                        val positionMs = intent.getLongExtra(PositionConstants.EXTRA_POSITION_MS, -1L)
                        android.util.Log.d("MainActivity", "Received position save broadcast: track=$trackIndex, position=${positionMs}ms")

                        try {
                            val messenger = flutterEngine.dartExecutor.binaryMessenger
                            val channel = MethodChannel(messenger, "com.jabook.app.jabook/audio_player")
                            channel.invokeMethod(
                                "saveCurrentPosition",
                                null,
                                object : MethodChannel.Result {
                                    override fun success(result: Any?) {
                                        android.util.Log.d("MainActivity", "Position save triggered successfully")
                                    }

                                    override fun error(
                                        errorCode: String,
                                        errorMessage: String?,
                                        errorDetails: Any?,
                                    ) {}

                                    override fun notImplemented() {}
                                },
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "Failed to invoke saveCurrentPosition method", e)
                        }
                    }
                }
            }
        val filter = IntentFilter(PositionConstants.ACTION_SAVE_POSITION_BEFORE_UNLOAD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionSaveReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(positionSaveReceiver, filter)
        }
    }

    private fun unregisterPositionSaveReceiver() {
        positionSaveReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        positionSaveReceiver = null
    }

    private fun registerExitAppReceiver() {
        exitAppReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == "com.jabook.app.jabook.EXIT_APP") {
                        val isInitializing = playerLifecycleHandler?.isPlayerInitializing ?: false
                        android.util.Log.d("MainActivity", "Exit app broadcast. isPlayerInitializing=$isInitializing")

                        if (isInitializing) {
                            android.util.Log.w("MainActivity", "Ignoring exit app: player is initializing")
                            return
                        }

                        android.util.Log.i("MainActivity", "Finishing activity")
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAndRemoveTask()
                            } else {
                                finishAffinity()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to finish activity", e)
                        }
                    }
                }
            }
        val filter = IntentFilter("com.jabook.app.jabook.EXIT_APP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(exitAppReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(exitAppReceiver, filter)
        }
    }

    private fun unregisterExitAppReceiver() {
        exitAppReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        exitAppReceiver = null
    }
}
