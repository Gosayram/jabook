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
  /// UI layers can listen to this stream to react to changes in playback state.
  Stream<PlaybackState> get playbackState => _playbackState.stream;

  /// Initializes and starts the audio service.
  ///
  /// Sets up the audio service, initializes the player, and registers listeners.
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

  /// Registers a listener for player events and emits [PlaybackState] updates.
  void _setupAudioPlayer() {
    _audioPlayer.playbackEventStream.listen((event) {
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

  /// Maps the just_audio processing state to [AudioProcessingState].
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

  /// Sets up audio focus management (interruptions, ducking, etc).
  Future<void> _setupAudioFocus() async {
    // TODO: Implement audio focus handling using audio_session.
  }

  /// Starts playback for the provided media URL.
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

  /// Pauses the current playback.
  ///
  /// Throws [AudioFailure] if pausing fails.
  Future<void> pauseMedia() async {
    try {
      await _audioPlayer.pause();
    } on Exception {
      throw const AudioFailure('Failed to pause media');
    }
  }

  /// Stops the current playback.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stopMedia() async {
    try {
      await _audioPlayer.stop();
    } on Exception {
      throw const AudioFailure('Failed to stop media');
    }
  }

  /// Seeks to a specific [position] in the current media.
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seekTo(Duration position) async {
    try {
      await _audioPlayer.seek(position);
    } on Exception {
      throw const AudioFailure('Failed to seek');
    }
  }

  /// Sets the playback [speed].
  ///
  /// Throws [AudioFailure] if changing speed fails.
  Future<void> setSpeed(double speed) async {
    try {
      await _audioPlayer.setSpeed(speed);
    } on Exception {
      throw const AudioFailure('Failed to set speed');
    }
  }

  /// Releases resources held by this handler.
  Future<void> dispose() async {
    await _audioPlayer.dispose();
    await _playbackState.drain();
    await _playbackState.close();
  }
}

/// Bridges audio_service callbacks to the just_audio player.
///
/// Implements [BaseAudioHandler] methods expected by audio_service.
class AudioPlayerHandler extends BaseAudioHandler {
  /// Creates a new [AudioPlayerHandler].
  ///
  /// [audioPlayer] is the just_audio instance used for playback.
  AudioPlayerHandler(this._audioPlayer);

  /// just_audio player instance used for media playback.
  final AudioPlayer _audioPlayer;

  @override
  Future<void> play() => _audioPlayer.play();

  @override
  Future<void> pause() => _audioPlayer.pause();

  @override
  Future<void> stop() => _audioPlayer.stop();

  // NOTE: BaseAudioHandler defines `seek(Duration position)`, not `seekTo`.
  @override
  Future<void> seek(Duration position) => _audioPlayer.seek(position);
}