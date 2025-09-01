import 'package:audio_service/audio_service.dart';
import 'package:just_audio/just_audio.dart';
import 'package:rxdart/rxdart.dart';
import '../errors/failures.dart';

class AudioServiceHandler {
  AudioHandler? _handler;
  final AudioPlayer _audioPlayer = AudioPlayer();
  final BehaviorSubject<PlaybackState> _playbackState = BehaviorSubject();

  Stream<PlaybackState> get playbackState => _playbackState.stream;

  Future<void> startService() async {
    try {
      _handler = await AudioService.init(
        config: const AudioServiceConfig(
          androidNotificationChannelId: 'com.example.jabook.audio',
          androidNotificationChannelName: 'JaBook Audio',
          androidNotificationChannelDescription: 'JaBook Audiobook Player',
          androidNotificationOngoing: true,
          androidNotificationVisibility: NotificationVisibility.public,
          androidStopForegroundOnPause: true,
          androidNotificationIcon: 'mipmap/ic_launcher',
          androidEnableQueue: true,
        ),
        artCacheArtSize: 512,
        preloadArtwork: true,
        systemNotifications: const SystemNotificationConfig(
          nextAction: MediaAction.next,
          pauseAction: MediaAction.pause,
          playAction: MediaAction.play,
          previousAction: MediaAction.previous,
          stopAction: MediaAction.stop,
        ),
        onReady: () => _setupAudioPlayer(),
        onTaskAction: _onTaskAction,
        onNotificationClicked: _onNotificationClicked,
      );

      // Configure notification and lock-screen controls
      await _setupNotificationControls();
      
      // Handle audio focus policies
      await _setupAudioFocus();
      
      // Set up 5/10/15 second skip actions
      await _setupSkipActions();
    } catch (e) {
      throw AudioFailure('Failed to start audio service: ${e.toString()}');
    }
  }

  void _setupAudioPlayer() {
    _audioPlayer.playbackEventStream.listen((event) {
      final state = _audioPlayer.process(event);
      _playbackState.add(state);
    });
  }

  Future<void> _setupNotificationControls() async {
    if (_handler == null) return;

    // Set up play/pause action
    _handler!.addAction(const MediaAction(
      androidIcon: 'drawable/ic_play_pause',
      label: 'Play/Pause',
      action: MediaAction.playPause,
    ));

    // Set up next action
    _handler!.addAction(const MediaAction(
      androidIcon: 'drawable/ic_skip_next',
      label: 'Next',
      action: MediaAction.next,
    ));

    // Set up previous action
    _handler!.addAction(const MediaAction(
      androidIcon: 'drawable/ic_skip_previous',
      label: 'Previous',
      action: MediaAction.previous,
    ));
  }

  Future<void> _setupAudioFocus() async {
    // TODO: Implement audio focus handling using audio_session package
    // This will handle interruptions from other apps
  }

  Future<void> _setupSkipActions() async {
    if (_handler == null) return;

    // Set up 5 second skip forward
    _handler!.addAction(MediaAction(
      androidIcon: 'drawable/ic_skip_forward_5',
      label: 'Skip +5s',
      action: 'skip_forward_5',
    ));

    // Set up 10 second skip forward
    _handler!.addAction(MediaAction(
      androidIcon: 'drawable/ic_skip_forward_10',
      label: 'Skip +10s',
      action: 'skip_forward_10',
    ));

    // Set up 15 second skip forward
    _handler!.addAction(MediaAction(
      androidIcon: 'drawable/ic_skip_forward_15',
      label: 'Skip +15s',
      action: 'skip_forward_15',
    ));

    // Set up 5 second skip backward
    _handler!.addAction(MediaAction(
      androidIcon: 'drawable/ic_skip_backward_5',
      label: 'Skip -5s',
      action: 'skip_backward_5',
    ));

    // Set up 10 second skip backward
    _handler!.addAction(MediaAction(
      androidIcon: 'drawable/ic_skip_backward_10',
      label: 'Skip -10s',
      action: 'skip_backward_10',
    ));

    // Set up 15 second skip backward
    _handler!.addAction(MediaAction(
      androidIcon: 'drawable/ic_skip_backward_15',
      label: 'Skip -15s',
      action: 'skip_backward_15',
    ));
  }

  void _onTaskAction(MediaAction action) {
    // Handle task actions from notification/lockscreen
    switch (action.action) {
      case MediaAction.play:
        _audioPlayer.play();
        break;
      case MediaAction.pause:
        _audioPlayer.pause();
        break;
      case MediaAction.stop:
        _audioPlayer.stop();
        break;
      case MediaAction.next:
        _audioPlayer.seekToNext();
        break;
      case MediaAction.previous:
        _audioPlayer.seekToPrevious();
        break;
      case 'skip_forward_5':
        _audioPlayer.seek(Duration(seconds: _audioPlayer.position.inSeconds + 5));
        break;
      case 'skip_forward_10':
        _audioPlayer.seek(Duration(seconds: _audioPlayer.position.inSeconds + 10));
        break;
      case 'skip_forward_15':
        _audioPlayer.seek(Duration(seconds: _audioPlayer.position.inSeconds + 15));
        break;
      case 'skip_backward_5':
        _audioPlayer.seek(Duration(seconds: _audioPlayer.position.inSeconds - 5));
        break;
      case 'skip_backward_10':
        _audioPlayer.seek(Duration(seconds: _audioPlayer.position.inSeconds - 10));
        break;
      case 'skip_backward_15':
        _audioPlayer.seek(Duration(seconds: _audioPlayer.position.inSeconds - 15));
        break;
    }
  }

  void _onNotificationClicked() {
    // Handle notification click - could open player screen
    // TODO: Implement navigation to player screen
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