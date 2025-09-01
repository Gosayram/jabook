import 'package:audio_service/audio_service.dart';
import 'package:just_audio/just_audio.dart';
import 'package:rxdart/rxdart.dart';
import '../errors/failures.dart';

class AudioServiceHandler {
  final AudioPlayer _audioPlayer = AudioPlayer();
  final BehaviorSubject<PlaybackState> _playbackState = BehaviorSubject();

  Stream<PlaybackState> get playbackState => _playbackState.stream;

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
          androidStopForegroundOnPause: true,
          androidNotificationIcon: 'mipmap/ic_launcher',
        ),
      );

      // Set up audio player event listener
      _setupAudioPlayer();
      
      // Handle audio focus policies
      await _setupAudioFocus();
    } catch (e) {
      throw AudioFailure('Failed to start audio service: ${e.toString()}');
    }
  }

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

  Future<void> _setupAudioFocus() async {
    // TODO: Implement audio focus handling using audio_session package
    // This will handle interruptions from other apps
  }

  Future<void> playMedia(String url) async {
    try {
      await _audioPlayer.setUrl(url);
      await _audioPlayer.play();
    } catch (e) {
      throw AudioFailure('Failed to play media: ${e.toString()}');
    }
  }

  Future<void> pauseMedia() async {
    try {
      await _audioPlayer.pause();
    } catch (e) {
      throw AudioFailure('Failed to pause media: ${e.toString()}');
    }
  }

  Future<void> stopMedia() async {
    try {
      await _audioPlayer.stop();
    } catch (e) {
      throw AudioFailure('Failed to stop media: ${e.toString()}');
    }
  }

  Future<void> seekTo(Duration position) async {
    try {
      await _audioPlayer.seek(position);
    } catch (e) {
      throw AudioFailure('Failed to seek: ${e.toString()}');
    }
  }

  Future<void> setSpeed(double speed) async {
    try {
      await _audioPlayer.setSpeed(speed);
    } catch (e) {
      throw AudioFailure('Failed to set speed: ${e.toString()}');
    }
  }

  Future<void> dispose() async {
    await _audioPlayer.dispose();
    await _playbackState.drain();
    await _playbackState.close();
  }
}

class AudioPlayerHandler extends BaseAudioHandler {
  final AudioPlayer _audioPlayer;

  AudioPlayerHandler(this._audioPlayer);

  @override
  Future<void> play() => _audioPlayer.play();

  @override
  Future<void> pause() => _audioPlayer.pause();

  @override
  Future<void> stop() => _audioPlayer.stop();

  @override
  Future<void> seekTo(Duration position) => _audioPlayer.seek(position);

  @override
  Future<void> seekToNext() => _audioPlayer.seekToNext();

  @override
  Future<void> seekToPrevious() => _audioPlayer.seekToPrevious();
}