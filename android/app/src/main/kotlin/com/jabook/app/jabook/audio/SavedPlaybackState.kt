package com.jabook.app.jabook.audio

/**
 * Data class to store playback state before player recreation.
 */
data class SavedPlaybackState(
    val currentIndex: Int,
    val currentPosition: Long,
    val isPlaying: Boolean,
)
