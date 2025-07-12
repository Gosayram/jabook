package com.jabook.app.features.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.repository.AudiobookRepository
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val playerManager: PlayerManager,
    private val audiobookRepository: AudiobookRepository,
    private val debugLogger: IDebugLogger,
) : ViewModel() {

    private val _currentAudiobook = MutableStateFlow<Audiobook?>(null)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    private val _isSpeedDialogVisible = MutableStateFlow(false)
    private val _isSleepTimerDialogVisible = MutableStateFlow(false)

    private val playbackState = playerManager.getPlaybackState()

    val uiState: StateFlow<PlayerUiState> =
        combine(_currentAudiobook, playbackState, _playbackSpeed, _sleepTimerMinutes, _isSpeedDialogVisible, _isSleepTimerDialogVisible) {
                states ->
                val currentPlaybackState = states[1] as PlaybackState
                PlayerUiState(
                    currentAudiobook = states[0] as? Audiobook,
                    playbackState = currentPlaybackState,
                    currentPosition = currentPlaybackState.currentPosition,
                    duration = currentPlaybackState.duration,
                    isPlaying = currentPlaybackState.isPlaying,
                    playbackSpeed = states[2] as Float,
                    sleepTimerMinutes = states[3] as Int,
                    isSpeedDialogVisible = states[4] as Boolean,
                    isSleepTimerDialogVisible = states[5] as Boolean,
                )
            }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = PlayerUiState())

    fun loadAudiobook(audiobook: Audiobook) {
        viewModelScope.launch {
            try {
                _currentAudiobook.value = audiobook
                playerManager.initializePlayer(audiobook)
                debugLogger.logInfo("Audiobook loaded: ${audiobook.title}")
            } catch (e: Exception) {
                debugLogger.logError("Error loading audiobook", e)
            }
        }
    }

    fun playPause() {
        viewModelScope.launch {
            try {
                if (uiState.value.isPlaying) {
                    playerManager.pause()
                } else {
                    playerManager.play()
                }
                savePlaybackPosition()
            } catch (e: Exception) {
                debugLogger.logError("Error toggling playback", e)
            }
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            try {
                playerManager.seekTo(position)
                savePlaybackPosition()
            } catch (e: Exception) {
                debugLogger.logError("Error seeking", e)
            }
        }
    }

    fun seekForward() {
        viewModelScope.launch {
            try {
                val currentPos = playerManager.getCurrentPosition()
                val newPos = (currentPos + 30_000).coerceAtMost(playerManager.getDuration())
                playerManager.seekTo(newPos)
                savePlaybackPosition()
            } catch (e: Exception) {
                debugLogger.logError("Error seeking forward", e)
            }
        }
    }

    fun seekBackward() {
        viewModelScope.launch {
            try {
                val currentPos = playerManager.getCurrentPosition()
                val newPos = (currentPos - 15_000).coerceAtLeast(0)
                playerManager.seekTo(newPos)
                savePlaybackPosition()
            } catch (e: Exception) {
                debugLogger.logError("Error seeking backward", e)
            }
        }
    }

    fun nextChapter() {
        viewModelScope.launch {
            try {
                playerManager.nextChapter()
                savePlaybackPosition()
            } catch (e: Exception) {
                debugLogger.logError("Error moving to next chapter", e)
            }
        }
    }

    fun previousChapter() {
        viewModelScope.launch {
            try {
                playerManager.previousChapter()
                savePlaybackPosition()
            } catch (e: Exception) {
                debugLogger.logError("Error moving to previous chapter", e)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            try {
                _playbackSpeed.value = speed
                playerManager.setPlaybackSpeed(speed)
                debugLogger.logInfo("Playback speed set to: $speed")
            } catch (e: Exception) {
                debugLogger.logError("Error setting playback speed", e)
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            try {
                _sleepTimerMinutes.value = minutes
                playerManager.setSleepTimer(minutes)
                debugLogger.logInfo("Sleep timer set to: $minutes minutes")
            } catch (e: Exception) {
                debugLogger.logError("Error setting sleep timer", e)
            }
        }
    }

    fun showSpeedDialog() {
        _isSpeedDialogVisible.value = true
    }

    fun hideSpeedDialog() {
        _isSpeedDialogVisible.value = false
    }

    fun showSleepTimerDialog() {
        _isSleepTimerDialogVisible.value = true
    }

    fun hideSleepTimerDialog() {
        _isSleepTimerDialogVisible.value = false
    }

    private fun savePlaybackPosition() {
        val audiobook = _currentAudiobook.value ?: return
        val position = playerManager.getCurrentPosition()

        viewModelScope.launch {
            try {
                audiobookRepository.updatePlaybackPosition(audiobook.id, position)
            } catch (e: Exception) {
                debugLogger.logError("Error saving playback position", e)
            }
        }
    }

    fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSleepTimer(minutes: Int): String {
        return if (minutes > 0) {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60

            if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d", hours, remainingMinutes)
            } else {
                String.format(Locale.getDefault(), "%02d", remainingMinutes)
            }
        } else {
            ""
        }
    }
}

data class PlayerUiState(
    val currentAudiobook: Audiobook? = null,
    val playbackState: PlaybackState = PlaybackState(),
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutes: Int = 0,
    val isSpeedDialogVisible: Boolean = false,
    val isSleepTimerDialogVisible: Boolean = false,
)
