package com.jabook.app.features.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFocusManager
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val debugLogger: IDebugLogger,
  ) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    fun requestAudioFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean {
      if (hasAudioFocus) return true

      val result =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val audioAttributes =
            AudioAttributes
              .Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()

          audioFocusRequest =
            AudioFocusRequest
              .Builder(AudioManager.AUDIOFOCUS_GAIN)
              .setAudioAttributes(audioAttributes)
              .setAcceptsDelayedFocusGain(true)
              .setOnAudioFocusChangeListener(listener)
              .build()

          audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
          // For Android 6.0-7.1 we use the old API, but without @Suppress
          @Suppress("DEPRECATION")
          audioManager.requestAudioFocus(
            listener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
          )
        }

      hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      debugLogger.logDebug("Audio focus request result: $result")
      return hasAudioFocus
    }

    fun abandonAudioFocus() {
      if (!hasAudioFocus) return

      val result =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
          // For Android 6.0-7.1 we use the old API
          @Suppress("DEPRECATION")
          audioManager.abandonAudioFocus(null)
        }

      hasAudioFocus = false
      debugLogger.logDebug("Audio focus abandoned, result: $result")
    }

    fun hasAudioFocus(): Boolean = hasAudioFocus
  }
