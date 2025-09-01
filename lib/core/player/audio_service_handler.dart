import 'package:audio_service/audio_service.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:just_audio/just_audio.dart';
import 'package:rxdart/rxdart.dart';

/// Handles audio service operations and playback state management.
///
/// This class manages the audio service lifecycle, handles media playback,
/// and maintains the playback state for background audio playback.
class AudioServiceHandler {
  /// Internal audio player instance for media playback.
  final AudioPlayer _audioPlayer = AudioPlayer();
  
  /// Stream controller for playback state updates.
  final BehaviorSubject<PlaybackState> _playbackState = BehaviorSubject();

  /// Gets the stream of playback state updates.
  ///
  /// This stream can be listened to by UI components to react
  /// to changes in the audio playback state.
  Stream<PlaybackState> get playbackState => _playbackState.stream;

  /// Initializes and starts the audio service.
  ///
  /// This method sets up the audio service with proper configuration,
  /// initializes the audio player, and sets up event listeners and
  /// audio focus handling.
  ///
  /// Throws [AudioFailure] if the service cannot be started.
  Future<void> startService() async {
    try {
      // Initialize audio service with basic configuration
      await AudioService.init(
        builder: () => AudioPlayerHandler(_audioPlayer),
        config: const AudioServiceConfig(
          androidNotificationChannelId: 'com.example.jabook.audio',
          androidNotificationChannelName: 'JaBook Audio',
          androidNotificationChannelDescription: 'JaBook Audiobook Player',
          androidNotificationOngoing: true,
        ),
      );

      // Set up audio player event listener
      _setupAudioPlayer();
      
      // Handle audio focus policies
      await _setupAudioFocus();
    } on Exception {
      throw const AudioFailure('Failed to start audio service');
    }
  }

  /// Sets up the audio player event listener.
  ///
  /// This method listens to audio player events and updates the
  /// playback state stream accordingly.
  void _setupAudioPlayer() {
    _audioPlayer.playbackEventStream.listen((event) {
      // Create a simple playback state
      final state = PlaybackState(
        processingState: _getProcessingState(),
        updatePosition: _audioPlayer.position,
        bufferedPosition: _audioPlayer.bufferedPosition,
        speed: _audioPlayer.speed,
        playing: _audioPlayer.playing,
      );
      _playbackState.add(state);
    });
  }

  /// Maps the internal processing state to AudioService state.
  ///
  /// This method converts the just_audio processing state to
  /// the corresponding AudioService processing state.
  ///
  /// Returns the appropriate [AudioProcessingState] based on
  /// the current audio player state.
  AudioProcessingState _getProcessingState() {
    if (_audioPlayer.playing) {
      return AudioProcessingState.ready;
    } else if (_audioPlayer.processingState == ProcessingState.loading) {
      return AudioProcessingState.loading;
    } else if (_audioPlayer.processingState == ProcessingState.buffering) {
      return AudioProcessingState.buffering;
    } else {
      return AudioProcessingState.idle;
    }
  }

  /// Sets up audio focus handling for interruptions.
  ///
  /// This method should be implemented using the audio_session package
  /// to handle audio focus requests from other applications and
  /// manage interruptions properly.
  Future<void> _setupAudioFocus() async {
    // TODO: Implement audio focus handling using audio_session package
    // This will handle interruptions from other apps
  }

  /// Starts playing media from the specified URL.
  ///
  /// This method sets the audio source to the provided URL and
  /// begins playback.
  ///
  /// The [url] parameter is the URL of the audio file to play.
  ///
  /// Throws [AudioFailure] if playback cannot be started.
  Future<void> playMedia(String url) async {
    try {
      await _audioPlayer.setUrl(url);
      await _audioPlayer.play();
    } on Exception {
      throw const AudioFailure('Failed to play media');
    }
  }

  /// Pauses the currently playing media.
  ///
  /// This method pauses the audio player and maintains the current
  /// playback position for resuming later.
  ///
  /// Throws [AudioFailure] if pausing fails.
  Future<void> pauseMedia() async {
    try {
      await _audioPlayer.pause();
    } on Exception {
      throw const AudioFailure('Failed to pause media');
    }
  }

  /// Stops the currently playing media.
  ///
  /// This method stops the audio player and resets the playback
  /// position to the beginning.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stopMedia() async {
    try {
      await _audioPlayer.stop();
    } on Exception {
      throw const AudioFailure('Failed to stop media');
    }
  }

  /// Seeks to the specified position in the current media.
  ///
  /// This method changes the playback position to the specified
  /// duration.
  ///
  /// The [position] parameter is the time position to seek to.
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seekTo(Duration position) async {
    try {
      await _audioPlayer.seek(position);
    } on Exception {
      throw const AudioFailure('Failed to seek');
    }
  }

  /// Sets the playback speed for the current media.
  ///
  /// This method changes the speed at which the audio is played,
  /// allowing for faster or slower playback.
  ///
  /// The [speed] parameter is the playback speed multiplier (1.0 = normal speed).
  ///
  /// Throws [AudioFailure] if setting speed fails.
  Future<void> setSpeed(double speed) async {
    try {
      await _audioPlayer.setSpeed(speed);
    } on Exception {
      throw const AudioFailure('Failed to set speed');
    }
  }

  /// Cleans up resources when the handler is no longer needed.
  ///
  /// This method disposes the audio player and closes the playback
  /// state stream to prevent memory leaks.
  Future<void> dispose() async {
    await _audioPlayer.dispose();
    await _playbackState.drain();
    await _playbackState.close();
  }
}

/// Audio service handler that implements BaseAudioHandler.
///
/// This class provides the interface between the audio service
/// and the just_audio player, handling all audio service callbacks.
class AudioPlayerHandler extends BaseAudioHandler {

  /// Creates a new AudioPlayerHandler instance.
  ///
  /// The [audioPlayer] parameter is the just_audio player instance
  /// that will handle the actual audio playback.
  AudioPlayerHandler(this._audioPlayer);
  
  /// The just_audio player instance for media playback.
  final AudioPlayer _audioPlayer;

  @override
  /// Starts or resumes playback of the current media.
  Future<void> play() => _audioPlayer.play();

  @override
  /// Pauses playback of the current media.
  Future<void> pause() => _audioPlayer.pause();

  @override
  /// Stops playback and resets to the beginning.
  Future<void> stop() => _audioPlayer.stop();

  @override
  /// Seeks to the specified position in the current media.
  Future<void> seekTo(Duration position) => _audioPlayer.seek(position);

}