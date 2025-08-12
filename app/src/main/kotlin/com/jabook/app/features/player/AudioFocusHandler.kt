package com.jabook.app.features.player

import android.media.AudioManager

class AudioFocusHandler(
    private val audioFocusManager: AudioFocusManager,
    private val listener: AudioManager.OnAudioFocusChangeListener,
) {

    fun requestAudioFocus(): Boolean {
        return audioFocusManager.requestAudioFocus(listener)
    }

    fun abandonAudioFocus() {
        audioFocusManager.abandonAudioFocus()
    }
}
