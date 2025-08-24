package com.jabook.app.features.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Bookmark
import com.jabook.app.core.domain.repository.AudiobookRepository
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: PlayerManager,
    private val audiobookRepository: AudiobookRepository,
    private val debugLogger: IDebugLogger,
  ) : ViewModel() {
    private val _currentAudiobook = MutableStateFlow<Audiobook?>(null)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    private val _isSpeedDialogVisible = MutableStateFlow(false)
    private val _isSleepTimerDialogVisible = MutableStateFlow(false)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    private val _isBookmarksSheetVisible = MutableStateFlow(false)

    private val playbackState = playerManager.getPlaybackState()

    val uiState: StateFlow<PlayerUiState> =
      combine(
        _currentAudiobook,
        playbackState,
        _playbackSpeed,
        _sleepTimerMinutes,
        _isSpeedDialogVisible,
        _isSleepTimerDialogVisible,
        _bookmarks,
        _isBookmarksSheetVisible,
      ) { flows ->
        val currentAudiobook = flows[0] as Audiobook?
        val playbackState = flows[1] as PlaybackState
        val playbackSpeed = flows[2] as Float
        val sleepTimerMinutes = flows[3] as Int
        val isSpeedDialogVisible = flows[4] as Boolean
        val isSleepTimerDialogVisible = flows[5] as Boolean
        val bookmarks = flows[6] as List<Bookmark>
        val isBookmarksSheetVisible = flows[7] as Boolean

        PlayerUiState(
          currentAudiobook = currentAudiobook,
          playbackState = playbackState,
          currentPosition = playbackState.currentPosition,
          duration = playbackState.duration,
          isPlaying = playbackState.isPlaying,
          playbackSpeed = playbackSpeed,
          sleepTimerMinutes = sleepTimerMinutes,
          isSpeedDialogVisible = isSpeedDialogVisible,
          isSleepTimerDialogVisible = isSleepTimerDialogVisible,
          bookmarks = bookmarks,
          isBookmarksSheetVisible = isBookmarksSheetVisible,
        )
      }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState(),
      )

    // Public properties with private backing fields
    val currentAudiobook: Audiobook? get() = _currentAudiobook.value
    val playbackSpeed: Float get() = _playbackSpeed.value
    val sleepTimerMinutes: Int get() = _sleepTimerMinutes.value
    val isSpeedDialogVisible: Boolean get() = _isSpeedDialogVisible.value
    val isSleepTimerDialogVisible: Boolean get() = _isSleepTimerDialogVisible.value
    val bookmarks: List<Bookmark> get() = _bookmarks.value
    val isBookmarksSheetVisible: Boolean get() = _isBookmarksSheetVisible.value

    fun loadAudiobook(audiobook: Audiobook) {
      viewModelScope.launch {
        try {
          _currentAudiobook.value = audiobook
          playerManager.initializePlayer(audiobook)

          // Start PlayerService for background playback
          PlayerService.startService(context, audiobook)

          debugLogger.logInfo("Audiobook loaded: ${audiobook.title}")
        } catch (e: Exception) {
          debugLogger.logError("Error loading audiobook", e)
        }
      }

      // Observe bookmarks flow in separate launch to avoid blocking
      viewModelScope.launch {
        try {
          audiobookRepository.getBookmarksByAudiobookId(audiobook.id).collect { list -> _bookmarks.value = list }
        } catch (e: Exception) {
          debugLogger.logError("Error observing bookmarks", e)
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

    fun showBookmarksSheet() {
      _isBookmarksSheetVisible.value = true
    }

    fun hideBookmarksSheet() {
      _isBookmarksSheetVisible.value = false
    }

    fun deleteBookmark(bookmark: Bookmark) {
      viewModelScope.launch {
        try {
          audiobookRepository.deleteBookmark(bookmark.id)
          debugLogger.logInfo("Deleted bookmark ${bookmark.id}")
        } catch (e: Exception) {
          debugLogger.logError("Error deleting bookmark", e)
        }
      }
    }

    /** Create a bookmark at the current playback position with autogenerated title. */
    fun addBookmark() {
      val audiobook = _currentAudiobook.value ?: return
      val position = playerManager.getCurrentPosition()

      viewModelScope.launch {
        try {
          val title = "Bookmark ${formatTime(position)}"
          val bookmark =
            Bookmark(id = UUID.randomUUID().toString(), audiobookId = audiobook.id, title = title, positionMs = position)
          audiobookRepository.upsertBookmark(bookmark)
          debugLogger.logInfo("Bookmark added at $position ms for audiobook ${audiobook.title}")
        } catch (e: Exception) {
          debugLogger.logError("Error adding bookmark", e)
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
      val audiobook = currentAudiobook ?: return
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

    fun formatSleepTimer(minutes: Int): String =
      if (minutes > 0) {
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

    override fun onCleared() {
      super.onCleared()
      // Stop PlayerService when ViewModel is cleared
      PlayerService.stopService(context)
      debugLogger.logInfo("PlayerViewModel cleared, PlayerService stopped")
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
  val bookmarks: List<Bookmark> = emptyList(),
  val isBookmarksSheetVisible: Boolean = false,
)
