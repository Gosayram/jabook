package com.jabook.app.jabook.audio.bridge

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.core.model.PlaybackState

/**
 * Listener that bridges ExoPlayer events to EventChannel.
 */
class BridgePlayerListener(
    private val eventChannelHandler: EventChannelHandler,
    private val getPlayer: () -> ExoPlayer,
) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable =
        object : Runnable {
            override fun run() {
                val player = getPlayer()
                if (player.isPlaying) {
                    sendStateUpdate()
                    handler.postDelayed(this, 1000) // Update every second
                }
            }
        }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        // Trigger on any event that might change the state we care about
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_PLAYER_ERROR,
            )
        ) {
            if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                player.playerError?.let { error ->
                    eventChannelHandler.sendError(
                        error.message ?: "Playback Error",
                        error.errorCodeName,
                    )
                }
            }
            sendStateUpdate()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startPeriodicUpdates()
        } else {
            stopPeriodicUpdates()
        }
    }

    private fun startPeriodicUpdates() {
        stopPeriodicUpdates()
        handler.post(updateRunnable)
    }

    private fun stopPeriodicUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    fun release() {
        stopPeriodicUpdates()
    }

    private fun sendStateUpdate() {
        try {
            val player = getPlayer()
            val state =
                PlaybackState(
                    isPlaying = player.isPlaying,
                    currentPosition = player.currentPosition,
                    duration = if (player.duration != -9223372036854775807L) player.duration else 0L, // C.TIME_UNSET handled
                    currentTrackIndex = player.currentMediaItemIndex,
                    playbackSpeed = player.playbackParameters.speed,
                    bufferedPosition = player.bufferedPosition,
                    playbackState = player.playbackState,
                )
            eventChannelHandler.sendPlaybackState(state)
        } catch (e: Exception) {
            android.util.Log.e("BridgePlayerListener", "Error sending state update", e)
        }
    }
}
