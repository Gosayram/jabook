// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/di/providers/player_providers.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/infrastructure/config/audio_settings_manager.dart';
import 'package:jabook/core/infrastructure/config/audio_settings_provider.dart';
import 'package:jabook/core/infrastructure/config/book_audio_settings_service.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/core/library/cover_fallback_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/playback_settings_provider.dart';
import 'package:jabook/core/player/player_state_provider.dart'
    show
        PlayerStateModel,
        PlayerStateNotifier,
        currentAudiobookGroupProvider,
        playerStateProvider;
import 'package:jabook/core/player/sleep_timer_service.dart';
import 'package:jabook/core/utils/responsive_utils.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/player/presentation/widgets/tracks_bottom_sheet.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Player screen for local audiobook files.
///
/// This screen provides the user interface for playing local audiobook files,
/// including playback controls, progress tracking, and automatic track switching.
class LocalPlayerScreen extends ConsumerStatefulWidget {
  /// Creates a new LocalPlayerScreen instance.
  ///
  /// The [group] parameter is required to identify which audiobook group
  /// should be displayed and played.
  LocalPlayerScreen({super.key, required this.group}) {
    // CRITICAL DEBUG: Log when widget is created
    debugPrint(
        '🟢 [PLAYER_INIT] LocalPlayerScreen constructor called with group: ${group.groupPath}');
  }

  /// The audiobook group to play.
  final LocalAudiobookGroup group;

  @override
  ConsumerState<LocalPlayerScreen> createState() => _LocalPlayerScreenState();
}

class _LocalPlayerScreenState extends ConsumerState<LocalPlayerScreen> {
  // CRITICAL DEBUG: Log when state is created
  _LocalPlayerScreenState() {
    debugPrint('🟢 [PLAYER_INIT] _LocalPlayerScreenState constructor called');
  }

  // Spacing constants for consistent UI
  static const double _kSpacingSmall = 8.0;
  static const double _kSpacingMedium = 16.0;
  static const double _kSpacingLarge = 24.0;

  /// Gets medium spacing based on screen size.
  ///
  /// Returns adaptive spacing for different screen sizes following MD3 guidelines.
  double _getMediumSpacing(BuildContext context) {
    if (ResponsiveUtils.isVerySmallScreen(context)) {
      return 12.0;
    } else if (ResponsiveUtils.isTablet(context)) {
      return 20.0;
    }
    return 16.0;
  }

  final PermissionService _permissionService = PermissionService();
  final StructuredLogger _logger = StructuredLogger();
  late final SleepTimerService _sleepTimerService;
  Timer? _sleepTimerUpdateTimer;
  bool _isPlayerLoading = false; // Player is being initialized
  bool _hasError = false;
  String? _errorMessage;
  // Local state for slider during dragging
  double? _sliderValue;
  bool _isDragging = false;
  int? _initialPositionMs; // Initial position when dragging starts
  String? _embeddedArtworkPath; // Path to embedded artwork from metadata
  String?
      _groupArtworkPath; // First found artwork path for the group (global setting)
  bool _showChapterChips =
      false; // Track if chapter chips list should be visible

  // MethodChannel for player lifecycle tracking (prevents app exit during initialization)
  static const MethodChannel _playerLifecycleChannel =
      MethodChannel('com.jabook.app.jabook/player_lifecycle');

  @override
  void initState() {
    super.initState();
    // CRITICAL DEBUG: Log immediately when initState is called
    debugPrint(
        '🟢 [PLAYER_INIT] initState() called for group: ${widget.group.groupPath}');

    // Initialize sleep timer service with native player
    // Create NativeAudioPlayer instance (it uses MethodChannel internally, so multiple instances share the same native service)
    _sleepTimerService = SleepTimerService(NativeAudioPlayer());

    // UI is ready to show immediately (no need for _isInitialized flag)
    // Player initialization will happen asynchronously in background
    // Store current group in provider for mini player navigation
    debugPrint('🟢 [PLAYER_INIT] Scheduling addPostFrameCallback...');
    WidgetsBinding.instance.addPostFrameCallback((_) {
      debugPrint('🟢 [PLAYER_INIT] addPostFrameCallback executed');
      if (!mounted) {
        debugPrint('🟡 [PLAYER_INIT] Widget not mounted, skipping');
        return;
      }
      try {
        debugPrint('🟢 [PLAYER_INIT] Setting currentAudiobookGroupProvider...');
        ref.read(currentAudiobookGroupProvider.notifier).state = widget.group;
        debugPrint('🟢 [PLAYER_INIT] Loading group artwork...');
        // Load first available artwork from group files (global setting)
        _loadGroupArtwork();
        debugPrint('🟢 [PLAYER_INIT] Starting _initializePlayer()...');
        // Start player initialization asynchronously (non-blocking)
        _initializePlayer();
        debugPrint(
            '🟢 [PLAYER_INIT] _initializePlayer() call completed (async)');
      } on Exception catch (e) {
        // Log error but don't block UI - player will initialize on next attempt
        debugPrint('🔴 [PLAYER_INIT] ERROR in postFrameCallback: $e');
        if (mounted) {
          setState(() {
            _hasError = true;
            _errorMessage = 'Failed to initialize player: ${e.toString()}';
          });
        }
      }
    });
    // Listen to player state changes to update metadata when track changes
    _setupMetadataListener();
  }

  @override
  void didUpdateWidget(LocalPlayerScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    // If group changed, reinitialize player
    if (oldWidget.group.groupPath != widget.group.groupPath) {
      setState(() {
        _isPlayerLoading = true;
        _hasError = false;
        _errorMessage = null;
        _embeddedArtworkPath = null;
        _groupArtworkPath = null;
      });
      // Stop previous playback before switching
      ref.read(playerStateProvider.notifier).stop();
      // Update current group
      ref.read(currentAudiobookGroupProvider.notifier).state = widget.group;
      // Load first available artwork from new group
      _loadGroupArtwork();
      // Reinitialize with new group
      _initializePlayer();
    }
  }

  void _setupMetadataListener() {
    // Listen to player state changes and update metadata when track index changes
    // Delay setup to ensure provider is ready - use addPostFrameCallback to avoid blocking
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      try {
        ref.listen(playerStateProvider, (previous, next) {
          if (!mounted) return;

          // Check if sleep timer is set to expire at end of chapter
          // Trigger it when current track ends (playbackState == STATE_ENDED or position >= duration)
          // "End of chapter" means end of current audio file, not transition to next track
          if (previous != null && _sleepTimerService.isAtEndOfChapter) {
            debugPrint(
                '🟡 [SLEEP_TIMER] Checking end of chapter: previous.playbackState=${previous.playbackState}, next.playbackState=${next.playbackState}, previous.position=${previous.currentPosition}, next.position=${next.currentPosition}, duration=${next.duration}');

            // Check if playback ended (STATE_ENDED = 4)
            final playbackEnded =
                previous.playbackState != 4 && next.playbackState == 4;

            // Also check if position reached or exceeded duration (more reliable for last track)
            final positionReachedEnd = next.duration > 0 &&
                next.currentPosition >=
                    next.duration - 100 && // Allow 100ms tolerance
                previous.currentPosition <
                    previous.duration - 100; // Was not at end before

            if (playbackEnded || positionReachedEnd) {
              debugPrint(
                  '🟢 [SLEEP_TIMER] Track ended detected (playbackEnded=$playbackEnded, positionReachedEnd=$positionReachedEnd), triggering sleep timer');
              _sleepTimerService.triggerAtEndOfChapter();
            }
          }

          if (previous?.currentIndex != next.currentIndex) {
            // Track changed, update metadata
            _updateMetadata();
            // Check for embedded artwork from metadata
            _checkEmbeddedArtwork();
          }
        });
      } on Exception catch (e) {
        // Provider not ready yet, will retry later
        debugPrint('Failed to setup metadata listener: $e');
        // Retry after a delay
        Future.delayed(const Duration(milliseconds: 500), () {
          if (mounted) {
            try {
              ref.listen(playerStateProvider, (previous, next) {
                if (!mounted) return;

                // Check if sleep timer is set to expire at end of chapter
                // Trigger it when current track ends (playbackState == STATE_ENDED or position >= duration)
                // "End of chapter" means end of current audio file, not transition to next track
                if (previous != null && _sleepTimerService.isAtEndOfChapter) {
                  debugPrint(
                      '🟡 [SLEEP_TIMER] Checking end of chapter (retry): previous.playbackState=${previous.playbackState}, next.playbackState=${next.playbackState}, previous.position=${previous.currentPosition}, next.position=${next.currentPosition}, duration=${next.duration}');

                  // Check if playback ended (STATE_ENDED = 4)
                  final playbackEnded =
                      previous.playbackState != 4 && next.playbackState == 4;

                  // Also check if position reached or exceeded duration (more reliable for last track)
                  final positionReachedEnd = next.duration > 0 &&
                      next.currentPosition >=
                          next.duration - 100 && // Allow 100ms tolerance
                      previous.currentPosition <
                          previous.duration - 100; // Was not at end before

                  if (playbackEnded || positionReachedEnd) {
                    debugPrint(
                        '🟢 [SLEEP_TIMER] Track ended detected (retry) (playbackEnded=$playbackEnded, positionReachedEnd=$positionReachedEnd), triggering sleep timer');
                    _sleepTimerService.triggerAtEndOfChapter();
                  }
                }

                if (previous?.currentIndex != next.currentIndex) {
                  _updateMetadata();
                  _checkEmbeddedArtwork();
                }
              });
            } on Exception catch (e2) {
              debugPrint('Failed to setup metadata listener on retry: $e2');
            }
          }
        });
      }
      // Also check for embedded artwork on initialization
      Future.delayed(const Duration(milliseconds: 500), () {
        if (mounted) {
          _checkEmbeddedArtwork();
        }
      });
    });
  }

  /// Loads the first available artwork from group files (global setting).
  /// This ensures we always use the first found artwork, even if the first file doesn't have one.
  Future<void> _loadGroupArtwork() async {
    try {
      // Try group coverPath first
      if (widget.group.coverPath != null) {
        final coverFile = File(widget.group.coverPath!);
        if (coverFile.existsSync()) {
          setState(() {
            _groupArtworkPath = widget.group.coverPath;
          });
          return;
        }
      }

      // Try to extract artwork from files in order until found
      final nativePlayer = NativeAudioPlayer();
      String? artworkPath;

      for (final file in widget.group.files) {
        try {
          artworkPath = await nativePlayer.extractArtworkFromFile(
            file.filePath,
          );

          if (artworkPath != null) {
            final artworkFile = File(artworkPath);
            if (artworkFile.existsSync()) {
              // Found artwork, stop searching and use this as group artwork
              if (mounted) {
                setState(() {
                  _groupArtworkPath = artworkPath;
                });
                // Update global state
                final updatedGroup =
                    widget.group.copyWith(coverPath: artworkPath);
                ref.read(currentAudiobookGroupProvider.notifier).state =
                    updatedGroup;
              }
              return;
            } else {
              artworkPath = null;
            }
          }
        } on Exception catch (e) {
          // Continue to next file if this one fails
          debugPrint('Failed to extract artwork from ${file.filePath}: $e');
          artworkPath = null;
        }
      }

      // If no artwork found, try online fallback
      if (artworkPath == null && widget.group.coverPath == null) {
        try {
          const fallbackService = CoverFallbackService();
          final fallbackPath = await fallbackService.fetchCoverFromOnline(
            widget.group.groupName,
            torrentId: widget.group.torrentId,
          );
          if (fallbackPath != null && mounted) {
            final fallbackFile = File(fallbackPath);
            if (fallbackFile.existsSync()) {
              setState(() {
                _groupArtworkPath = fallbackPath;
              });
              // Update global state
              final updatedGroup =
                  widget.group.copyWith(coverPath: fallbackPath);
              ref.read(currentAudiobookGroupProvider.notifier).state =
                  updatedGroup;
            }
          }
        } on Exception catch (e) {
          // Silently fail - online fallback is optional
          safeUnawaited(_logger.log(
            level: 'debug',
            subsystem: 'player',
            message: 'Failed to fetch cover from online fallback',
            cause: e.toString(),
          ));
        }
      }
    } on Exception catch (e) {
      // Silently fail - embedded artwork is optional
      safeUnawaited(_logger.log(
        level: 'debug',
        subsystem: 'player',
        message: 'Failed to load group artwork',
        cause: e.toString(),
      ));
    }
  }

  Future<void> _checkEmbeddedArtwork() async {
    // Use group artwork (first found) as primary source (global setting)
    // Only check current track artwork if group artwork is not available
    if (_groupArtworkPath != null) {
      final artworkFile = File(_groupArtworkPath!);
      if (artworkFile.existsSync()) {
        setState(() {
          _embeddedArtworkPath = _groupArtworkPath;
        });
        return;
      }
    }

    try {
      // Fallback: Get current media item info which includes artworkPath
      // Use Media3PlayerService through provider to ensure singleton instance
      final playerService = ref.read(media3PlayerServiceProvider);
      final mediaInfo = await playerService.getCurrentMediaItemInfo();
      final artworkPath = mediaInfo['artworkPath'] as String?;
      if (artworkPath != null && artworkPath.isNotEmpty) {
        final artworkFile = File(artworkPath);
        if (artworkFile.existsSync()) {
          setState(() {
            _embeddedArtworkPath = artworkPath;
          });
        }
      }
    } on Exception catch (e) {
      // Silently fail - embedded artwork is optional
      safeUnawaited(_logger.log(
        level: 'debug',
        subsystem: 'player',
        message: 'Failed to check embedded artwork',
        cause: e.toString(),
      ));
    }
  }

  Future<void> _initializePlayer() async {
    // CRITICAL DEBUG: Log immediately to verify method is called
    debugPrint('🔵 [PLAYER_INIT] _initializePlayer() called - START');

    // CRITICAL: Notify native side that player is initializing
    // This prevents app exit during initialization (white screen fix)
    debugPrint('🔵 [PLAYER_INIT] Attempting to set isPlayerInitializing flag');
    try {
      debugPrint('🔵 [PLAYER_INIT] Calling _logger.log()...');
      await _logger.log(
        level: 'info',
        subsystem: 'player',
        message:
            'Starting player initialization, setting isPlayerInitializing flag',
      );
      debugPrint(
          '🔵 [PLAYER_INIT] _logger.log() completed, calling MethodChannel...');
      await _playerLifecycleChannel.invokeMethod<bool>(
        'setPlayerInitializing',
        {'isInitializing': true},
      );
      debugPrint('🔵 [PLAYER_INIT] MethodChannel completed successfully');
      await _logger.log(
        level: 'info',
        subsystem: 'player',
        message: 'isPlayerInitializing flag set successfully',
      );
      debugPrint('🔵 [PLAYER_INIT] isPlayerInitializing flag set - SUCCESS');
    } on Exception catch (e) {
      // Log but don't block - this is a safety feature
      debugPrint('🔴 [PLAYER_INIT] ERROR setting flag: $e');
      await _logger.log(
        level: 'warning',
        subsystem: 'player',
        message: 'Failed to set player initializing flag',
        cause: e.toString(),
      );
      debugPrint('Failed to set player initializing flag: $e');
    }

    // Mark player as loading (UI is already shown)
    if (mounted) {
      setState(() {
        _isPlayerLoading = true;
        _hasError = false;
        _errorMessage = null;
      });
    }

    try {
      // Safely access player state provider - wrap in try-catch to handle
      // cases where provider might not be ready yet
      PlayerStateNotifier? playerNotifier;
      PlayerStateModel? currentState;

      try {
        playerNotifier = ref.read(playerStateProvider.notifier);
        currentState = ref.read(playerStateProvider);
      } on Exception catch (e) {
        // Provider not ready yet, wait a bit and retry
        debugPrint('Player state provider not ready yet: $e');
        await Future.delayed(const Duration(milliseconds: 100));
        if (!mounted) return;
        try {
          playerNotifier = ref.read(playerStateProvider.notifier);
          currentState = ref.read(playerStateProvider);
        } on Exception catch (e2) {
          // Still not ready, show error
          if (mounted) {
            setState(() {
              _isPlayerLoading = false;
              _hasError = true;
              _errorMessage = 'Player service not ready. Please try again.';
            });
          }
          await _logger.log(
            level: 'error',
            subsystem: 'audio',
            message: 'Failed to access player state provider',
            extra: {'error': e2.toString()},
          );

          // CRITICAL: Notify native side that player initialization failed
          try {
            await _playerLifecycleChannel.invokeMethod<bool>(
              'setPlayerInitializing',
              {'isInitializing': false},
            );
          } on Exception catch (e) {
            debugPrint('Failed to clear player initializing flag: $e');
          }
          return;
        }
      }

      if (playerNotifier == null || currentState == null) {
        if (mounted) {
          setState(() {
            _isPlayerLoading = false;
            _hasError = true;
            _errorMessage = 'Player service not available';
          });
        }

        // CRITICAL: Notify native side that player initialization failed
        try {
          await _playerLifecycleChannel.invokeMethod<bool>(
            'setPlayerInitializing',
            {'isInitializing': false},
          );
        } on Exception catch (e) {
          debugPrint('Failed to clear player initializing flag: $e');
        }
        return;
      }

      // Check if player is already initialized and playing the same group
      // If yes, skip reinitialization to avoid interrupting playback
      if (currentState.currentGroupPath == widget.group.groupPath &&
          currentState.playbackState != 0) {
        // Player is already playing this group - just mark as initialized
        // No need to reload or restore position, player is already at correct position
        await _logger.log(
          level: 'info',
          subsystem: 'audio',
          message:
              'Player already initialized for this group, skipping reinitialization',
          extra: {
            'group_path': widget.group.groupPath,
            'current_position': currentState.currentPosition,
            'current_track': currentState.currentIndex,
          },
        );
        if (mounted) {
          setState(() {
            _isPlayerLoading = false;
            _hasError = false;
            _errorMessage = null;
          });
        }

        // CRITICAL: Notify native side that player initialization is complete
        // (Player was already initialized, so no initialization needed)
        try {
          await _playerLifecycleChannel.invokeMethod<bool>(
            'setPlayerInitializing',
            {'isInitializing': false},
          );
        } on Exception catch (e) {
          // Log but don't block - this is a safety feature
          debugPrint('Failed to clear player initializing flag: $e');
        }
        return;
      }

      // Player is not initialized or playing different group - full initialization needed
      // Stop any existing playback first to avoid conflicts
      try {
        await playerNotifier.stop();
      } on Exception {
        // Ignore errors when stopping (might not be playing)
      }

      // Initialize player service (only if not already initialized)
      // Media3PlayerService checks _isInitialized internally, but we can skip if already ready
      debugPrint('🔵 [PLAYER_INIT] Checking if player needs initialization...');
      if (currentState.playbackState == 0) {
        debugPrint(
            '🔵 [PLAYER_INIT] Player state is 0 (idle), calling initialize()...');
        await playerNotifier.initialize();
        debugPrint('🔵 [PLAYER_INIT] Player initialize() completed');
      } else {
        debugPrint(
            '🔵 [PLAYER_INIT] Player already initialized (playbackState=${currentState.playbackState}), skipping');
      }

      // CRITICAL OPTIMIZATION: Get saved position FIRST to determine initialTrackIndex
      // This allows us to load only the needed track synchronously, others load asynchronously
      // This dramatically speeds up player startup for large playlists
      Map<String, int>? savedPosition;
      int? initialTrackIndex;
      try {
        savedPosition = await playerNotifier
            .restorePosition(widget.group.groupPath)
            .timeout(
          const Duration(milliseconds: 500),
          onTimeout: () {
            // Log timeout (non-blocking, don't await)
            _logger.log(
              level: 'info',
              subsystem: 'audio',
              message:
                  'Position restore timeout (500ms), starting from beginning',
            );
            return null;
          },
        );

        // Extract initialTrackIndex from savedPosition if valid
        if (savedPosition != null) {
          final trackIndex = savedPosition['trackIndex'];
          final positionMs = savedPosition['positionMs'];
          if (trackIndex != null &&
              trackIndex >= 0 &&
              trackIndex < widget.group.files.length &&
              positionMs != null &&
              positionMs > 0) {
            initialTrackIndex = trackIndex;
            await _logger.log(
              level: 'info',
              subsystem: 'audio',
              message: 'Using saved position for initial track loading',
              extra: {
                'initial_track_index': initialTrackIndex,
                'position_ms': positionMs,
              },
            );
          } else {
            savedPosition = null; // Invalid saved position
          }
        }
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Failed to restore position, starting from beginning',
          cause: e.toString(),
        );
        savedPosition = null;
      }

      // Load audio sources with initialTrackIndex optimization
      // Only the initial track (or first track) is loaded synchronously
      // Remaining tracks load asynchronously in background for fast startup
      await _loadAudioSources(initialTrackIndex: initialTrackIndex);

      // Apply audio settings (speed and skip duration) in parallel with waiting for ready
      // This speeds up initialization
      await Future.wait([
        _waitForPlayerReady(),
        _applyAudioSettings(),
      ]);

      // Start playback - either from saved position or from beginning
      if (mounted) {
        final currentState = ref.read(playerStateProvider);

        // Check if we have valid saved position
        if (savedPosition != null) {
          final trackIndex = savedPosition['trackIndex']!;
          final positionMs = savedPosition['positionMs']!;

          if (trackIndex >= 0 &&
              trackIndex < widget.group.files.length &&
              positionMs > 0) {
            // Valid saved position - restore it
            try {
              // Player should already be ready from _waitForPlayerReady() above
              // Just do a quick check (max 500ms) to ensure it's ready
              var attempts = 0;
              while (attempts < 5 && mounted) {
                final state = ref.read(playerStateProvider);
                if (state.playbackState == 2) {
                  // 2 = ready
                  break;
                }
                await Future.delayed(const Duration(milliseconds: 100));
                attempts++;
              }

              if (mounted) {
                // Use optimized method to seek to track and position at once
                await playerNotifier.seekToTrackAndPosition(
                  trackIndex,
                  Duration(milliseconds: positionMs),
                );

                // Start playback if not already playing
                if (!currentState.isPlaying) {
                  await playerNotifier.play();
                }

                await _logger.log(
                  level: 'info',
                  subsystem: 'audio',
                  message: 'Restored playback position',
                  extra: {
                    'track_index': trackIndex,
                    'position_ms': positionMs,
                  },
                );
              }
            } on Exception catch (e) {
              await _logger.log(
                level: 'warning',
                subsystem: 'audio',
                message: 'Failed to restore position, starting from beginning',
                cause: e.toString(),
              );
              // Fall through to start from beginning
              savedPosition = null; // Mark as invalid to start from beginning
            }
          } else {
            // Invalid saved position
            savedPosition = null;
          }
        }

        // If no valid saved position, start from beginning
        if (savedPosition == null && mounted) {
          await _logger.log(
            level: 'info',
            subsystem: 'audio',
            message: 'Starting playback from beginning',
          );

          // Player should already be ready from _waitForPlayerReady() above
          // No need to wait - start playback immediately
          final state = ref.read(playerStateProvider);
          if (!state.isPlaying && mounted) {
            try {
              await playerNotifier.play();
              await _logger.log(
                level: 'info',
                subsystem: 'audio',
                message: 'Playback started from beginning',
              );
            } on Exception catch (e) {
              await _logger.log(
                level: 'error',
                subsystem: 'audio',
                message: 'Failed to start playback',
                cause: e.toString(),
              );
            }
          }
        }
      }

      // Restore repeat mode and sleep timer from saved state
      final savedFullState =
          await ref.read(playerStateProvider.notifier).restoreFullState();
      if (savedFullState != null &&
          savedFullState.groupPath == widget.group.groupPath) {
        // Restore repeat mode
        final repeatMode = RepeatMode.values[
            savedFullState.repeatMode.clamp(0, RepeatMode.values.length - 1)];
        await ref
            .read(playbackSettingsProvider.notifier)
            .setRepeatMode(repeatMode);

        // Restore sleep timer if active
        // CRITICAL: Restore timer BEFORE clearing isPlayerInitializing flag
        // This ensures timer callback won't trigger exit during initialization
        // Note: When restoring, use simple pause callback to avoid issues
        // User can set new timer if they want exit behavior
        if (savedFullState.sleepTimerRemainingSeconds != null &&
            savedFullState.sleepTimerRemainingSeconds! > 0) {
          await _logger.log(
            level: 'info',
            subsystem: 'player',
            message: 'Restoring sleep timer from saved state',
            extra: {
              'remaining_seconds': savedFullState.sleepTimerRemainingSeconds,
            },
          );

          // CRITICAL: Check if timer already expired (remaining seconds <= 0)
          // If expired, don't restore it to prevent immediate callback
          if (savedFullState.sleepTimerRemainingSeconds! > 0) {
            await _sleepTimerService.startTimer(
              Duration(seconds: savedFullState.sleepTimerRemainingSeconds!),
              () {
                if (mounted) {
                  ref.read(playerStateProvider.notifier).pause();
                  final localizations = AppLocalizations.of(context);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(localizations?.sleepTimerPaused ??
                          'Sleep timer: Playback paused'),
                    ),
                  );
                }
              },
            );
            _startSleepTimerUpdates();
          } else {
            await _logger.log(
              level: 'warning',
              subsystem: 'player',
              message:
                  'Sleep timer from saved state already expired, not restoring',
            );
          }
        }
      }

      // Clear any previous errors and mark player as ready
      // UI was already shown, now player is ready
      if (mounted) {
        setState(() {
          _isPlayerLoading = false;
          _hasError = false;
          _errorMessage = null;
        });
      }

      // CRITICAL: Notify native side that player initialization is complete
      // This must be done AFTER restoring sleep timer to prevent timer callback
      // from triggering exit during initialization
      try {
        await _logger.log(
          level: 'info',
          subsystem: 'player',
          message:
              'Player initialization complete, clearing isPlayerInitializing flag',
        );
        await _playerLifecycleChannel.invokeMethod<bool>(
          'setPlayerInitializing',
          {'isInitializing': false},
        );
        await _logger.log(
          level: 'info',
          subsystem: 'player',
          message: 'isPlayerInitializing flag cleared successfully',
        );
      } on Exception catch (e) {
        // Log but don't block - this is a safety feature
        await _logger.log(
          level: 'error',
          subsystem: 'player',
          message: 'Failed to clear player initializing flag',
          cause: e.toString(),
        );
        debugPrint('Failed to clear player initializing flag: $e');
      }
    } on AudioFailure catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to initialize player',
        cause: e.toString(),
      );
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        var errorMessage = e.message;

        // Localize common audio error messages
        if (e.message.contains('Failed to start audio service')) {
          final errorMatch = RegExp(r'Failed to start audio service: (.+)')
              .firstMatch(e.message);
          errorMessage = localizations?.failedToStartAudioService(
                  errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to play media')) {
          final errorMatch =
              RegExp(r'Failed to play media: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToPlayMedia(errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to pause media')) {
          final errorMatch =
              RegExp(r'Failed to pause media: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToPauseMedia(errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to stop media')) {
          final errorMatch =
              RegExp(r'Failed to stop media: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToStopMedia(errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to seek')) {
          final errorMatch =
              RegExp(r'Failed to seek: (.+)').firstMatch(e.message);
          errorMessage =
              localizations?.failedToSeek(errorMatch?.group(1) ?? e.message) ??
                  e.message;
        } else if (e.message.contains('Failed to set speed')) {
          final errorMatch =
              RegExp(r'Failed to set speed: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToSetSpeed(errorMatch?.group(1) ?? e.message) ??
              e.message;
        }

        setState(() {
          _isPlayerLoading = false;
          _hasError = true;
          _errorMessage = errorMessage;
        });

        // CRITICAL: Notify native side that player initialization failed
        try {
          await _playerLifecycleChannel.invokeMethod<bool>(
            'setPlayerInitializing',
            {'isInitializing': false},
          );
        } on Exception catch (e) {
          debugPrint('Failed to clear player initializing flag: $e');
        }
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Unexpected error during player initialization',
        cause: e.toString(),
      );
      if (mounted) {
        setState(() {
          _isPlayerLoading = false; // Loading finished (with error)
          _hasError = true;
          _errorMessage =
              AppLocalizations.of(context)?.failedToLoadAudioMessage ??
                  'Failed to load audio: ${e.toString()}';
        });
      }

      // CRITICAL: Notify native side that player initialization failed
      try {
        await _playerLifecycleChannel.invokeMethod<bool>(
          'setPlayerInitializing',
          {'isInitializing': false},
        );
      } on Exception catch (e) {
        debugPrint('Failed to clear player initializing flag: $e');
      }
    }
  }

  /// Loads audio sources with optimized lazy loading.
  ///
  /// [initialTrackIndex] is optional track index to load first (for saved position optimization).
  /// If provided, only this track is loaded synchronously, others load asynchronously in background.
  /// This dramatically speeds up player startup for large playlists.
  Future<void> _loadAudioSources({int? initialTrackIndex}) async {
    try {
      // Check permissions first
      final hasPermission = await _permissionService.hasStoragePermission();

      if (!hasPermission) {
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Storage permission not granted, requesting...',
        );
        // Request permission
        final granted = await _permissionService.requestStoragePermission();
        if (!granted) {
          await _logger.log(
            level: 'error',
            subsystem: 'audio',
            message: 'Storage permission denied',
          );
          throw const AudioFailure(
            'Storage permission is required to play local files. '
            'Please grant permission in app settings.',
          );
        }
      }

      // Verify files exist and are accessible (check in parallel for faster startup)
      final filePaths = widget.group.files.map((f) => f.filePath).toList();

      // Check all files in parallel for faster startup
      final fileChecks = await Future.wait(
        filePaths.map((filePath) async {
          final file = File(filePath);
          final exists = await file.exists();
          return exists ? filePath : null;
        }),
      );

      final accessibleFiles = fileChecks.whereType<String>().toList();

      // Log missing files (non-blocking)
      if (accessibleFiles.length < filePaths.length) {
        final missingCount = filePaths.length - accessibleFiles.length;
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Some files not found, skipping',
          extra: {
            'missing_count': missingCount,
            'total_files': filePaths.length
          },
        );
      }

      if (accessibleFiles.isEmpty) {
        await _logger.log(
          level: 'error',
          subsystem: 'audio',
          message: 'No accessible audio files found',
          extra: {'total_files': filePaths.length},
        );
        final errorMessage = mounted
            ? (AppLocalizations.of(context)?.noAccessibleAudioFiles ??
                'No accessible audio files found')
            : 'No accessible audio files found';
        throw AudioFailure(errorMessage);
      }

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Loading audio sources',
        extra: {
          'accessible_files': accessibleFiles.length,
          'total_files': filePaths.length,
          'initial_track_index': initialTrackIndex,
        },
      );

      // Load audio sources with initialTrackIndex optimization
      // Only the initial track (or first track) is loaded synchronously
      // Remaining tracks load asynchronously in background for fast startup
      final metadata = <String, String>{
        'title': widget.group.groupName,
        if (widget.group.files.firstOrNull?.author != null)
          'artist': widget.group.files.firstOrNull!.author!,
        if (widget.group.coverPath != null)
          'coverPath': widget.group.coverPath!,
      };

      final playerNotifier = ref.read(playerStateProvider.notifier);
      await playerNotifier.setPlaylist(
        accessibleFiles,
        metadata: metadata,
        groupPath: widget.group.groupPath,
        initialTrackIndex: initialTrackIndex,
      );

      // Update currentAudiobookGroupProvider to ensure all UI components are synchronized
      // This ensures mini player, notification, and main player all use the same data source
      ref.read(currentAudiobookGroupProvider.notifier).state = widget.group;

      // Update metadata after playlist is set
      _updateMetadata();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to load audio sources',
        cause: e.toString(),
      );
      final errorMessage = mounted
          ? (AppLocalizations.of(context)?.failedToLoadAudioMessage ??
              'Failed to load audio: ${e.toString()}')
          : 'Failed to load audio: ${e.toString()}';
      throw AudioFailure(errorMessage);
    }
  }

  void _playPause() {
    final playerNotifier = ref.read(playerStateProvider.notifier);
    final state = ref.read(playerStateProvider);
    if (state.isPlaying) {
      playerNotifier.pause();
    } else {
      playerNotifier.play();
    }
  }

  Future<void> _seekToTrack(int index) async {
    if (index >= 0 && index < widget.group.files.length) {
      final playerNotifier = ref.read(playerStateProvider.notifier);
      await playerNotifier.seekToTrack(index);
      await playerNotifier.seek(Duration.zero);

      // Start playback automatically after switching track
      final currentState = ref.read(playerStateProvider);
      if (!currentState.isPlaying) {
        await playerNotifier.play();
      }
    }
  }

  void _prevTrack() {
    final state = ref.read(playerStateProvider);
    if (state.currentIndex > 0) {
      ref.read(playerStateProvider.notifier).previous();
    }
  }

  void _nextTrack() {
    final state = ref.read(playerStateProvider);
    if (state.currentIndex < widget.group.files.length - 1) {
      ref.read(playerStateProvider.notifier).next();
    }
  }

  /// Shows the tracks bottom sheet for navigation.
  void _showTracksBottomSheet() {
    if (widget.group.files.isEmpty) return;

    final scrollController = DraggableScrollableController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => TracksBottomSheet(
        group: widget.group,
        onTrackSelected: _seekToTrack,
        scrollController: scrollController,
      ),
    );
  }

  /// Updates metadata for current track.
  void _updateMetadata() {
    if (widget.group.files.isEmpty) return;
    final state = ref.read(playerStateProvider);
    if (state.currentIndex < 0 ||
        state.currentIndex >= widget.group.files.length) {
      return;
    }
    final currentFile = widget.group.files[state.currentIndex];

    // Build title with fallback to filename if displayName is empty
    final title = currentFile.displayName.isNotEmpty
        ? '${widget.group.groupName} — ${currentFile.displayName}'
        : (widget.group.groupName.isNotEmpty
            ? widget.group.groupName
            : currentFile.fileName);

    final metadata = <String, String>{
      'title': title,
      if (currentFile.author != null && currentFile.author!.isNotEmpty)
        'artist': currentFile.author!,
      if (widget.group.groupName.isNotEmpty) 'album': widget.group.groupName,
      if (widget.group.coverPath != null) 'coverPath': widget.group.coverPath!,
    };
    ref.read(playerStateProvider.notifier).updateMetadata(metadata);
  }

  /// Applies audio settings (speed and skip duration) from settings or book-specific settings.
  Future<void> _applyAudioSettings() async {
    try {
      final groupPath = widget.group.groupPath;
      final bookSettingsService = BookAudioSettingsService();
      final audioSettings = ref.read(audioSettingsProvider);

      // Check for individual book settings first
      final bookSettings = await bookSettingsService.getSettings(groupPath);

      // Determine which settings to use
      final playbackSpeed =
          bookSettings?.playbackSpeed ?? audioSettings.defaultPlaybackSpeed;
      final rewindDuration =
          bookSettings?.rewindDuration ?? audioSettings.defaultRewindDuration;
      final forwardDuration =
          bookSettings?.forwardDuration ?? audioSettings.defaultForwardDuration;

      // Apply playback speed if different from current
      final currentState = ref.read(playerStateProvider);
      if ((currentState.playbackSpeed - playbackSpeed).abs() > 0.01) {
        await ref.read(playerStateProvider.notifier).setSpeed(playbackSpeed);
      }

      // Update skip durations in MediaSessionManager
      try {
        final playerService = ref.read(media3PlayerServiceProvider);
        await playerService.updateSkipDurations(
            rewindDuration, forwardDuration);
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Failed to update skip durations in MediaSessionManager',
          cause: e.toString(),
        );
      }

      // Update inactivity timeout
      try {
        final playerService = ref.read(media3PlayerServiceProvider);
        await playerService.setInactivityTimeoutMinutes(
            audioSettings.inactivityTimeoutMinutes);
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Failed to set inactivity timeout',
          cause: e.toString(),
        );
      }

      // Apply audio processing settings
      try {
        final audioSettingsManager = AudioSettingsManager();
        final playerService = ref.read(media3PlayerServiceProvider);

        // Get global settings
        final normalizeVolume = await audioSettingsManager.getNormalizeVolume();
        final volumeBoostLevel =
            await audioSettingsManager.getVolumeBoostLevel();
        final drcLevel = await audioSettingsManager.getDRCLevel();
        final speechEnhancer = await audioSettingsManager.getSpeechEnhancer();
        final autoVolumeLeveling =
            await audioSettingsManager.getAutoVolumeLeveling();

        // Override with book-specific settings if available
        final finalNormalizeVolume =
            bookSettings?.normalizeVolume ?? normalizeVolume;
        final finalVolumeBoostLevel =
            bookSettings?.volumeBoostLevel ?? volumeBoostLevel;
        final finalDrcLevel = bookSettings?.drcLevel ?? drcLevel;
        final finalSpeechEnhancer =
            bookSettings?.speechEnhancer ?? speechEnhancer;
        final finalAutoVolumeLeveling =
            bookSettings?.autoVolumeLeveling ?? autoVolumeLeveling;

        // Apply audio processing settings
        await playerService.configureAudioProcessing(
          normalizeVolume: finalNormalizeVolume,
          volumeBoostLevel: finalVolumeBoostLevel,
          drcLevel: finalDrcLevel,
          speechEnhancer: finalSpeechEnhancer,
          autoVolumeLeveling: finalAutoVolumeLeveling,
        );

        await _logger.log(
          level: 'info',
          subsystem: 'audio',
          message: 'Applied audio processing settings',
          extra: {
            'normalizeVolume': finalNormalizeVolume,
            'volumeBoostLevel': finalVolumeBoostLevel,
            'drcLevel': finalDrcLevel,
            'speechEnhancer': finalSpeechEnhancer,
            'autoVolumeLeveling': finalAutoVolumeLeveling,
          },
        );
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Failed to apply audio processing settings',
          cause: e.toString(),
        );
      }

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Applied audio settings',
        extra: {
          'group_path': groupPath,
          'playback_speed': playbackSpeed,
          'rewind_duration': rewindDuration,
          'forward_duration': forwardDuration,
          'from_book_settings': bookSettings != null,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'audio',
        message: 'Failed to apply audio settings',
        cause: e.toString(),
      );
    }
  }

  /// Gets the rewind duration for current book.
  Future<int> _getRewindDuration() async {
    final groupPath = widget.group.groupPath;
    final bookSettingsService = BookAudioSettingsService();
    final audioSettings = ref.read(audioSettingsProvider);

    final bookSettings = await bookSettingsService.getSettings(groupPath);
    return bookSettings?.rewindDuration ?? audioSettings.defaultRewindDuration;
  }

  /// Gets the forward duration for current book.
  Future<int> _getForwardDuration() async {
    final groupPath = widget.group.groupPath;
    final bookSettingsService = BookAudioSettingsService();
    final audioSettings = ref.read(audioSettingsProvider);

    final bookSettings = await bookSettingsService.getSettings(groupPath);
    return bookSettings?.forwardDuration ??
        audioSettings.defaultForwardDuration;
  }

  /// Resets book audio settings to global defaults.
  Future<void> _resetBookSettings() async {
    try {
      final groupPath = widget.group.groupPath;
      final bookSettingsService = BookAudioSettingsService();

      // Remove individual settings
      await bookSettingsService.removeSettings(groupPath);

      // Apply global settings
      await _applyAudioSettings();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.settingsResetToGlobal ??
                  'Settings reset to global defaults',
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Reset book settings to global',
        extra: {'group_path': groupPath},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to reset book settings',
        cause: e.toString(),
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.errorResettingSettings ??
                  'Error resetting settings',
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    }
  }

  /// Changes playback speed.
  ///
  /// [speed] is the playback speed (0.5 to 2.0).
  Future<void> _setSpeed(double speed) async {
    try {
      await ref.read(playerStateProvider.notifier).setSpeed(speed);

      // Save as individual book setting
      final groupPath = widget.group.groupPath;
      final bookSettingsService = BookAudioSettingsService();
      await bookSettingsService.updateSettings(
        groupPath,
        BookAudioSettings(playbackSpeed: speed),
      );
    } on AudioFailure catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to set playback speed',
        cause: e.toString(),
      );
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(localizations?.failedToChangeSpeed(e.message) ??
                  'Failed to change speed: ${e.message}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // CRITICAL DEBUG: Log when build is called
    debugPrint('🟡 [PLAYER_INIT] build() called');

    // Safely access player state provider - handle cases where provider might not be ready yet
    debugPrint('🟡 [PLAYER_INIT] Attempting to watch playerStateProvider...');
    PlayerStateModel playerState;
    try {
      playerState = ref.watch(playerStateProvider);
      debugPrint('🟡 [PLAYER_INIT] playerStateProvider watched successfully');
    } on Exception catch (e) {
      // Provider not ready yet, use default state
      debugPrint(
          '🔴 [PLAYER_INIT] Player state provider not ready in build: $e');
      playerState = const PlayerStateModel(
        isPlaying: false,
        currentPosition: 0,
        duration: 0,
        currentIndex: 0,
        playbackSpeed: 1.0,
        playbackState: 0,
      );
    }
    debugPrint(
        '🟡 [PLAYER_INIT] Building UI with playerState: playbackState=${playerState.playbackState}');

    // Show loading indicator only when player is being initialized or buffering
    // UI (cover, interface) is shown immediately
    final isLoading =
        _isPlayerLoading || playerState.playbackState == 1; // 1 = buffering
    // Only show error if player initialization is complete and not loading
    // This prevents showing error messages during initial loading
    final hasError = !isLoading && (_hasError || playerState.error != null);
    final errorMessage = _errorMessage ?? playerState.error;

    // Extract author from groupName (format: "Author - Title")
    String? authorName;
    final groupNameParts = widget.group.groupName.split(' - ');
    if (groupNameParts.length > 1) {
      authorName = groupNameParts.first.trim();
    } else {
      // If no " - " separator, try to get author from first file
      if (widget.group.files.isNotEmpty &&
          widget.group.files.first.author != null &&
          widget.group.files.first.author!.isNotEmpty) {
        authorName = widget.group.files.first.author;
      } else {
        authorName = widget.group.groupName;
      }
    }

    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        centerTitle: true, // Center the author name
        toolbarHeight: 48, // Compact height adapted to text
        title: Text(
          authorName ?? widget.group.groupName,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontSize: 16,
              ),
        ),
      ),
      body: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onVerticalDragEnd: (details) {
          // Swipe down to close (return to mini player)
          if (details.primaryVelocity != null &&
              details.primaryVelocity! > 500) {
            context.pop();
          }
        },
        child: hasError
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline,
                          size: 64, color: Colors.red),
                      const SizedBox(height: _kSpacingMedium),
                      Text(
                        AppLocalizations.of(context)?.failedToLoadAudio ??
                            'Failed to load audio',
                        style: Theme.of(context).textTheme.titleMedium,
                        textAlign: TextAlign.center,
                      ),
                      if (errorMessage != null) ...[
                        const SizedBox(height: _kSpacingSmall),
                        Text(
                          errorMessage,
                          style: Theme.of(context).textTheme.bodyMedium,
                          textAlign: TextAlign.center,
                        ),
                      ],
                      const SizedBox(height: _kSpacingLarge),
                      ElevatedButton(
                        onPressed: () {
                          setState(() {
                            _isPlayerLoading = true;
                            _hasError = false;
                            _errorMessage = null;
                          });
                          _initializePlayer();
                        },
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              )
            : Stack(
                // Show UI immediately, loading overlay on top if needed
                children: [
                  // Main UI content with CustomScrollView for proper scrolling
                  CustomScrollView(
                    slivers: [
                      // Top section: Cover + Player
                      SliverToBoxAdapter(
                        child: Column(
                          children: [
                            // Cover image - full width, no padding to fill all space
                            _buildCoverImage(),
                            // Rest of content with padding
                            Padding(
                              padding:
                                  ResponsiveUtils.getCompactPadding(context),
                              child: Column(
                                children: [
                                  const SizedBox(height: 4),
                                  // Track/chapter title - large, prominent
                                  _buildTrackTitle(playerState),
                                  const SizedBox(height: 6),
                                  // Author and book name - subtitle style
                                  _buildTrackSubtitle(),
                                  const SizedBox(height: 8),
                                  // Progress slider with 16dp horizontal padding
                                  Padding(
                                    padding: const EdgeInsets.symmetric(
                                        horizontal: 16.0),
                                    child: _buildProgressSlider(playerState),
                                  ),
                                  const SizedBox(height: 8),
                                  // Time indicators directly under progress bar
                                  Padding(
                                    padding: const EdgeInsets.symmetric(
                                        horizontal: 16.0),
                                    child: _buildTimeIndicators(playerState),
                                  ),
                                  // Track info and percentage (if multiple tracks)
                                  if (widget.group.files.length > 1) ...[
                                    const SizedBox(height: 8),
                                    Padding(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 16.0),
                                      child: _buildTrackInfo(playerState),
                                    ),
                                  ],
                                  SizedBox(height: _getMediumSpacing(context)),
                                  // Current track indicator and navigation
                                  if (widget.group.files.length > 1)
                                    _buildTrackNavigation(playerState),
                                  SizedBox(
                                      height:
                                          _getMediumSpacing(context) * 0.75),
                                  // Playback controls
                                  _buildPlaybackControls(playerState),
                                  const SizedBox(height: 12),
                                  // Speed, repeat and sleep timer controls in one row
                                  _buildControlsRow(playerState),
                                  // Bottom padding for system navigation gestures
                                  SizedBox(
                                    height:
                                        MediaQuery.of(context).padding.bottom >
                                                0
                                            ? MediaQuery.of(context)
                                                    .padding
                                                    .bottom +
                                                6
                                            : 8,
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  // Loading overlay (only when player is initializing)
                  if (isLoading)
                    Positioned.fill(
                      child: ColoredBox(
                        color: Colors.black.withValues(alpha: 0.3),
                        child: const Center(
                          child: CircularProgressIndicator(),
                        ),
                      ),
                    ),
                ],
              ),
      ),
    );
  }

  Widget _buildCoverImage() {
    // Cover image stretched to full width with no side padding
    final screenWidth = MediaQuery.of(context).size.width;

    // Use 4:3 aspect ratio for cover (taller than 16:9 to use more space)
    // Height will be calculated automatically by AspectRatio widget

    // Try embedded artwork from metadata first (if available)
    if (_embeddedArtworkPath != null) {
      final embeddedFile = File(_embeddedArtworkPath!);
      if (embeddedFile.existsSync()) {
        return RepaintBoundary(
          child: AspectRatio(
            aspectRatio: 4 / 3,
            child: Image.file(
              embeddedFile,
              width: screenWidth,
              fit: BoxFit.cover,
              cacheWidth: (screenWidth * 2).round(), // 2x for retina displays
              errorBuilder: (context, error, stackTrace) =>
                  _buildGroupCover(screenWidth, 0),
            ),
          ),
        );
      }
    }

    // Fallback to group cover path
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: AspectRatio(
            aspectRatio: 4 / 3,
            child: Image.file(
              coverFile,
              width: screenWidth,
              fit: BoxFit.cover,
              cacheWidth: (screenWidth * 2).round(), // 2x for retina displays
              errorBuilder: (context, error, stackTrace) =>
                  _buildDefaultCover(screenWidth, 0),
            ),
          ),
        );
      }
    }
    return _buildDefaultCover(screenWidth, 0);
  }

  Widget _buildGroupCover(double width, double horizontalPadding) {
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: AspectRatio(
            aspectRatio: 4 / 3,
            child: Image.file(
              coverFile,
              width: width,
              fit: BoxFit.cover,
              cacheWidth: (width * 2).round(),
              errorBuilder: (context, error, stackTrace) =>
                  _buildDefaultCover(width, horizontalPadding),
            ),
          ),
        );
      }
    }
    return _buildDefaultCover(width, horizontalPadding);
  }

  Widget _buildDefaultCover(double width, double horizontalPadding) =>
      AspectRatio(
        aspectRatio: 4 / 3,
        child: Container(
          width: width,
          decoration: BoxDecoration(
            color: Colors.grey[300],
          ),
          child: const Icon(Icons.audiotrack, size: 80, color: Colors.grey),
        ),
      );

  /// Builds track/chapter title - large, prominent.
  Widget _buildTrackTitle(PlayerStateModel state) {
    final hasFiles = widget.group.files.isNotEmpty &&
        state.currentIndex >= 0 &&
        state.currentIndex < widget.group.files.length;

    final chapterNumber = hasFiles ? state.chapterNumberValue : 1;
    final localizations = AppLocalizations.of(context);
    final chapterText =
        localizations?.chapterNumber(chapterNumber) ?? 'Chapter $chapterNumber';

    return Text(
      chapterText,
      style: Theme.of(context).textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.bold,
          ),
      textAlign: TextAlign.center,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// Builds track subtitle - book name only (without author).
  Widget _buildTrackSubtitle() {
    // Extract book title from groupName (format: "Author - Title")
    String bookTitle;
    final groupNameParts = widget.group.groupName.split(' - ');
    if (groupNameParts.length > 1) {
      bookTitle = groupNameParts.sublist(1).join(' - ').trim();
    } else {
      bookTitle = widget.group.groupName;
    }

    return Text(
      bookTitle,
      style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color:
                Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
            fontSize: 13,
          ),
      textAlign: TextAlign.center,
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// Builds track info - "Track X of Y" and percentage in one line.
  Widget _buildTrackInfo(PlayerStateModel state) {
    final totalTracks = widget.group.files.length;
    final currentTrack = state.chapterNumberValue.clamp(1, totalTracks);
    final progress =
        totalTracks > 0 ? (currentTrack / totalTracks).clamp(0.0, 1.0) : 0.0;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          AppLocalizations.of(context)
                  ?.trackOfTotal(currentTrack, totalTracks) ??
              'Track $currentTrack of $totalTracks',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context)
                    .colorScheme
                    .onSurface
                    .withValues(alpha: 0.6),
              ),
        ),
        Text(
          '${(progress * 100).toStringAsFixed(0)}%',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context)
                    .colorScheme
                    .onSurface
                    .withValues(alpha: 0.6),
              ),
        ),
      ],
    );
  }

  Widget _buildProgressSlider(PlayerStateModel state) {
    final hasDuration = state.duration > 0;
    final hasError = state.error != null;

    // Calculate progress - ensure it's always clamped to 0.0-1.0
    final progress = _isDragging && _sliderValue != null
        ? _sliderValue!.clamp(0.0, 1.0)
        : (hasDuration
            ? (state.currentPosition / state.duration).clamp(0.0, 1.0)
            : 0.0);

    // Enable seek if we have duration and no errors
    // Allow seeking even during loading for better UX
    final canSeek = hasDuration && !hasError;

    return Slider(
      value: progress.clamp(0.0, 1.0),
      onChanged: canSeek ? _onSliderChanged : null,
      onChangeStart: canSeek ? _onSliderStart : null,
      onChangeEnd: canSeek ? _onSliderEnd : null,
    );
  }

  /// Called when user starts dragging the slider.
  void _onSliderStart(double value) {
    final state = ref.read(playerStateProvider);
    setState(() {
      _isDragging = true;
      _sliderValue = value;
      _initialPositionMs = state.currentPosition;
    });
  }

  /// Called while user is dragging the slider.
  void _onSliderChanged(double value) {
    setState(() {
      _sliderValue = value.clamp(0.0, 1.0);
    });
  }

  /// Called when user finishes dragging the slider.
  void _onSliderEnd(double value) {
    final state = ref.read(playerStateProvider);
    final positionMs =
        (value * state.duration).round().clamp(0, state.duration);
    final position = Duration(milliseconds: positionMs);

    // Perform actual seek
    ref.read(playerStateProvider.notifier).seek(position);

    // Reset local state
    setState(() {
      _isDragging = false;
      _sliderValue = null;
      _initialPositionMs = null;
    });
  }

  /// Builds time indicators - current time on left, total time on right.
  /// Shows skip information when dragging.
  Widget _buildTimeIndicators(PlayerStateModel state) {
    if (_isDragging && _sliderValue != null && _initialPositionMs != null) {
      // Show skip information during dragging
      final newPositionMs =
          (_sliderValue! * state.duration).round().clamp(0, state.duration);
      final skipMs = newPositionMs - _initialPositionMs!;
      final skipDuration = Duration(milliseconds: skipMs.abs());
      final isForward = skipMs > 0;

      return Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Current position (initial)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppLocalizations.of(context)?.currentPosition ?? 'Current',
                    style: Theme.of(context).textTheme.labelSmall,
                  ),
                  Text(
                    _formatDuration(
                        Duration(milliseconds: _initialPositionMs!)),
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
              // Skip duration
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: Theme.of(context)
                      .colorScheme
                      .primary
                      .withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isForward ? Icons.forward : Icons.replay,
                      size: 16,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(width: _kSpacingSmall / 2),
                    Text(
                      '${isForward ? '+' : '-'}${_formatDuration(skipDuration)}',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: Theme.of(context).colorScheme.primary,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ],
                ),
              ),
              // New position
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    AppLocalizations.of(context)?.newPosition ?? 'New',
                    style: Theme.of(context).textTheme.labelSmall,
                  ),
                  Text(
                    _formatDuration(Duration(milliseconds: newPositionMs)),
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Theme.of(context).colorScheme.primary,
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: _kSpacingSmall / 2),
          // Total duration
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Text(
                _formatDuration(Duration(milliseconds: state.duration)),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ],
      );
    } else {
      // Normal display when not dragging
      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(_formatDuration(Duration(milliseconds: state.currentPosition))),
          Text(_formatDuration(Duration(milliseconds: state.duration))),
        ],
      );
    }
  }

  /// Waits for player to be ready (playbackState == 2).
  ///
  /// This ensures that seek controls are enabled only when player is fully initialized.
  /// Optimized for fast startup - only waits up to 1 second.
  Future<void> _waitForPlayerReady() async {
    // Check current state first - if already ready, don't wait
    final initialState = ref.read(playerStateProvider);
    if (initialState.playbackState == 2) {
      return; // Already ready
    }
    if (initialState.error != null) {
      return; // Error occurred, don't wait
    }

    // Wait for player to become ready with shorter timeout (1 second max)
    var attempts = 0;
    const maxAttempts = 10; // 1 second max wait (10 * 100ms)
    while (attempts < maxAttempts && mounted) {
      final state = ref.read(playerStateProvider);
      if (state.playbackState == 2) {
        // Player is ready
        return;
      }
      if (state.error != null) {
        // Error occurred, don't wait further
        return;
      }
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;
    }
    // If we reach here, player didn't become ready in time
    // This is not necessarily an error - player might still be loading
    // Continue anyway to allow playback to start
  }

  /// Builds compact chapters/tracks navigation block.
  Widget _buildTrackNavigation(PlayerStateModel state) {
    if (widget.group.files.isEmpty) return const SizedBox.shrink();
    if (state.currentIndex < 0 ||
        state.currentIndex >= widget.group.files.length) {
      return const SizedBox.shrink();
    }

    final currentIndex = state.currentIndex;
    final files = widget.group.files;
    final totalTracks = files.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Header: "Chapters" with counter "X / Y" - clickable to toggle chips list
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          child: Row(
            children: [
              // Left side: Icon and "Chapters" label - opens full list
              InkWell(
                onTap: _showTracksBottomSheet,
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      vertical: 8.0, horizontal: 4.0),
                  child: Row(
                    children: [
                      Icon(
                        Icons.list,
                        size: 22,
                        color: Theme.of(context).colorScheme.onSurface,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        AppLocalizations.of(context)?.chaptersLabel ??
                            'Chapters',
                        style:
                            Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                      ),
                    ],
                  ),
                ),
              ),
              const Spacer(),
              // Right side: "X / Y >" - toggles chips list visibility
              InkWell(
                onTap: () {
                  setState(() {
                    _showChapterChips = !_showChapterChips;
                  });
                },
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      vertical: 8.0, horizontal: 4.0),
                  child: Row(
                    children: [
                      Text(
                        '${state.chapterNumberValue} / $totalTracks',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurface
                                  .withValues(alpha: 0.6),
                            ),
                      ),
                      const SizedBox(width: 4),
                      Icon(
                        _showChapterChips
                            ? Icons.expand_less
                            : Icons.expand_more,
                        size: 20,
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.6),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        // Horizontal list of track chips - shown only when _showChapterChips is true
        if (_showChapterChips) ...[
          const SizedBox(height: 12),
          SizedBox(
            height: 60,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              itemCount: files.length,
              itemBuilder: (context, index) {
                final isCurrent = index == currentIndex;
                return Padding(
                  padding: const EdgeInsets.only(right: 8.0),
                  child: FilterChip(
                    label: Text('${index + 1}'),
                    selected: isCurrent,
                    onSelected: (_) {
                      // Start playback automatically when track is selected
                      _seekToTrack(index);
                    },
                    selectedColor: Theme.of(context).primaryColor,
                    checkmarkColor: Theme.of(context).colorScheme.onPrimary,
                    labelStyle: TextStyle(
                      color: isCurrent
                          ? Theme.of(context).colorScheme.onPrimary
                          : null,
                      fontWeight:
                          isCurrent ? FontWeight.bold : FontWeight.normal,
                    ),
                    side: isCurrent
                        ? BorderSide(
                            color: Theme.of(context).primaryColor,
                            width: 2,
                          )
                        : null,
                  ),
                );
              },
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildPlaybackControls(PlayerStateModel state) {
    final isLoading = state.playbackState == 1; // 1 = buffering

    return Column(
      children: [
        // First row: Main controls (previous, rewind, play/pause, forward, next)
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Previous track button
            IconButton(
              icon: const Icon(Icons.skip_previous),
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 28 : 32,
              ),
              onPressed: state.currentIndex > 0 ? _prevTrack : null,
              tooltip: 'Previous track',
              constraints: const BoxConstraints(
                minWidth: 48.0, // Material 3 minimum touch target
                minHeight: 48.0,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.isVerySmallScreen(context) ? 4 : 8,
            ),
            // Rewind button
            IconButton(
              icon: const Icon(Icons.replay_10),
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 24 : 28,
              ),
              onPressed: isLoading ? null : _rewind,
              tooltip: 'Rewind',
              constraints: const BoxConstraints(
                minWidth: 48.0, // Material 3 minimum touch target
                minHeight: 48.0,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.isVerySmallScreen(context) ? 4 : 8,
            ),
            // Play/Pause button
            IconButton(
              icon: Icon(state.isPlaying ? Icons.pause : Icons.play_arrow),
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 40 : 48,
              ),
              onPressed: isLoading ? null : _playPause,
              tooltip: state.isPlaying ? 'Pause' : 'Play',
              constraints: const BoxConstraints(
                minWidth:
                    56.0, // Larger touch target for main play/pause button
                minHeight: 56.0,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.isVerySmallScreen(context) ? 4 : 8,
            ),
            // Forward button
            IconButton(
              icon: const Icon(Icons.forward_30),
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 24 : 28,
              ),
              onPressed: isLoading ? null : _forward,
              tooltip: 'Forward',
              constraints: const BoxConstraints(
                minWidth: 48.0, // Material 3 minimum touch target
                minHeight: 48.0,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.isVerySmallScreen(context) ? 4 : 8,
            ),
            // Next track button
            IconButton(
              icon: const Icon(Icons.skip_next),
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 28 : 32,
              ),
              onPressed: state.currentIndex < widget.group.files.length - 1
                  ? _nextTrack
                  : null,
              tooltip: 'Next track',
              constraints: const BoxConstraints(
                minWidth: 48.0, // Material 3 minimum touch target
                minHeight: 48.0,
              ),
            ),
          ],
        ),
      ],
    );
  }

  /// Rewinds playback by configured seconds.
  Future<void> _rewind([int? seconds]) async {
    final rewindSeconds = seconds ?? await _getRewindDuration();
    final state = ref.read(playerStateProvider);
    final currentPosition = Duration(milliseconds: state.currentPosition);
    final newPosition = currentPosition - Duration(seconds: rewindSeconds);
    final actualNewPosition =
        newPosition.isNegative ? Duration.zero : newPosition;

    await ref.read(playerStateProvider.notifier).seek(actualNewPosition);
  }

  /// Forwards playback by configured seconds.
  Future<void> _forward([int? seconds]) async {
    final forwardSeconds = seconds ?? await _getForwardDuration();
    final state = ref.read(playerStateProvider);
    final currentPosition = Duration(milliseconds: state.currentPosition);
    final duration = Duration(milliseconds: state.duration);
    final newPosition = currentPosition + Duration(seconds: forwardSeconds);
    final actualNewPosition = newPosition > duration ? duration : newPosition;

    await ref.read(playerStateProvider.notifier).seek(actualNewPosition);
  }

  /// Builds a row with speed, repeat and sleep timer controls.
  Widget _buildControlsRow(PlayerStateModel state) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          // Speed control
          _buildSpeedControl(state),
          // Repeat mode control
          _buildRepeatControl(),
          // Sleep timer control
          _buildSleepTimerControl(),
        ],
      );

  Widget _buildSpeedControl(PlayerStateModel state) => PopupMenuButton<double?>(
        tooltip: 'Playback speed',
        itemBuilder: (context) {
          final localizations = AppLocalizations.of(context);
          final speeds = AudioSettingsManager.getAvailablePlaybackSpeeds();
          final items = speeds.map((speed) {
            final isSelected = (state.playbackSpeed - speed).abs() < 0.01;
            return PopupMenuItem<double?>(
              value: speed,
              child: Row(
                children: [
                  if (isSelected)
                    const Icon(Icons.check, size: 20)
                  else
                    const SizedBox(width: 20),
                  const SizedBox(width: 8),
                  Text(AudioSettingsManager.formatPlaybackSpeed(speed)),
                ],
              ),
            );
          }).toList();

          // Add divider and reset option if book has individual settings
          return [
            ...items,
            const PopupMenuDivider(),
            PopupMenuItem<double?>(
              child: Row(
                children: [
                  const Icon(Icons.restore, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    localizations?.resetToGlobalSettings ??
                        'Reset to global settings',
                  ),
                ],
              ),
            ),
          ];
        },
        onSelected: (value) async {
          if (value == null) {
            // Reset to global settings
            await _resetBookSettings();
          } else {
            await _setSpeed(value);
          }
        },
        child: Chip(
          avatar: const Icon(Icons.speed, size: 18),
          label: Text(
              AudioSettingsManager.formatPlaybackSpeed(state.playbackSpeed)),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        ),
      );

  Widget _buildRepeatControl() {
    final settings = ref.watch(playbackSettingsProvider);
    final repeatMode = settings.repeatMode;

    return IconButton(
      icon: Icon(
        repeatMode == RepeatMode.track
            ? Icons.repeat_one
            : repeatMode == RepeatMode.playlist
                ? Icons.repeat
                : Icons.repeat_outlined,
        color: repeatMode != RepeatMode.none
            ? Theme.of(context).primaryColor
            : null,
      ),
      onPressed: () async {
        final settingsNotifier = ref.read(playbackSettingsProvider.notifier);
        await settingsNotifier.cycleRepeatMode();
        // Update saved state with new repeat mode
        final newSettings = ref.read(playbackSettingsProvider);
        await ref.read(playerStateProvider.notifier).updateSavedStateSettings(
              repeatMode: newSettings.repeatMode.index,
            );
      },
      tooltip: _getRepeatModeTooltip(repeatMode),
    );
  }

  String _getRepeatModeTooltip(RepeatMode mode) {
    final localizations = AppLocalizations.of(context);
    switch (mode) {
      case RepeatMode.none:
        return localizations?.noRepeat ?? 'No repeat';
      case RepeatMode.track:
        return localizations?.repeatTrack ?? 'Repeat track';
      case RepeatMode.playlist:
        return localizations?.repeatPlaylist ?? 'Repeat playlist';
    }
  }

  Widget _buildSleepTimerControl() {
    final isActive = _sleepTimerService.isActive;
    final remainingSeconds = _sleepTimerService.remainingSeconds;
    final localizations = AppLocalizations.of(context);

    if (isActive && remainingSeconds != null) {
      final durationText = _formatDuration(Duration(seconds: remainingSeconds));
      return PopupMenuButton<Duration?>(
        tooltip: localizations?.sleepTimerTooltip(durationText) ??
            'Sleep timer: $durationText',
        onSelected: (duration) async {
          debugPrint(
              '🟡 [SLEEP_TIMER_UI] Cancel selected, duration: $duration');
          if (duration == null) {
            debugPrint('🟢 [SLEEP_TIMER_UI] Cancelling timer...');
            await _sleepTimerService.cancelTimer();
            _stopSleepTimerUpdates();
            await ref
                .read(playerStateProvider.notifier)
                .updateSavedStateSettings();
            if (mounted) {
              setState(() {});
              debugPrint(
                  '🟢 [SLEEP_TIMER_UI] Timer cancelled, UI updated. isActive: ${_sleepTimerService.isActive}');
            }
          } else {
            debugPrint(
                '🔴 [SLEEP_TIMER_UI] Unexpected duration value: $duration');
          }
        },
        itemBuilder: (context) => [
          PopupMenuItem<Duration?>(
            onTap: () async {
              // Also handle onTap as fallback
              debugPrint('🟡 [SLEEP_TIMER_UI] Cancel tapped (onTap)');
              await _sleepTimerService.cancelTimer();
              _stopSleepTimerUpdates();
              await ref
                  .read(playerStateProvider.notifier)
                  .updateSavedStateSettings();
              if (mounted) {
                setState(() {});
                debugPrint(
                    '🟢 [SLEEP_TIMER_UI] Timer cancelled via onTap, UI updated');
              }
            },
            child: Text(localizations?.cancelTimerButton ?? 'Cancel timer'),
          ),
        ],
        child: Chip(
          avatar: const Icon(Icons.timer, size: 18),
          label: Text(durationText),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          backgroundColor:
              Theme.of(context).primaryColor.withValues(alpha: 0.1),
        ),
      );
    }

    if (isActive && remainingSeconds == null) {
      final endOfChapterText =
          localizations?.endOfChapterLabel ?? 'End of chapter';
      return PopupMenuButton<Duration?>(
        tooltip: localizations?.atEndOfChapterLabel ?? 'At end of chapter',
        onSelected: (duration) async {
          debugPrint(
              '🟡 [SLEEP_TIMER_UI] Cancel selected, duration: $duration');
          if (duration == null) {
            debugPrint('🟢 [SLEEP_TIMER_UI] Cancelling timer...');
            await _sleepTimerService.cancelTimer();
            _stopSleepTimerUpdates();
            await ref
                .read(playerStateProvider.notifier)
                .updateSavedStateSettings();
            if (mounted) {
              setState(() {});
              debugPrint(
                  '🟢 [SLEEP_TIMER_UI] Timer cancelled, UI updated. isActive: ${_sleepTimerService.isActive}');
            }
          } else {
            debugPrint(
                '🔴 [SLEEP_TIMER_UI] Unexpected duration value: $duration');
          }
        },
        itemBuilder: (context) => [
          PopupMenuItem<Duration?>(
            onTap: () async {
              // Also handle onTap as fallback
              debugPrint('🟡 [SLEEP_TIMER_UI] Cancel tapped (onTap)');
              await _sleepTimerService.cancelTimer();
              _stopSleepTimerUpdates();
              await ref
                  .read(playerStateProvider.notifier)
                  .updateSavedStateSettings();
              if (mounted) {
                setState(() {});
                debugPrint(
                    '🟢 [SLEEP_TIMER_UI] Timer cancelled via onTap, UI updated');
              }
            },
            child: Text(localizations?.cancelTimerButton ?? 'Cancel timer'),
          ),
        ],
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: MediaQuery.of(context).size.width * 0.3,
          ),
          child: Chip(
            avatar: const Icon(Icons.timer, size: 18),
            label: Text(
              endOfChapterText,
              overflow: TextOverflow.ellipsis,
              maxLines: 1,
            ),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            backgroundColor:
                Theme.of(context).primaryColor.withValues(alpha: 0.1),
          ),
        ),
      );
    }

    // Show selected duration even when timer is not active (like speed control)
    final selectedDuration = _sleepTimerService.selectedDuration;
    String displayText;
    if (selectedDuration == null) {
      displayText = localizations?.timerLabel ?? 'Timer';
    } else if (selectedDuration == const Duration(seconds: -1)) {
      displayText = localizations?.endOfChapterLabel ?? 'End of chapter';
    } else {
      // Format duration for display
      if (selectedDuration.inHours > 0) {
        displayText = localizations?.sleepTimerHour ?? '1 hour';
      } else {
        final minutes = selectedDuration.inMinutes;
        displayText =
            localizations?.sleepTimerMinutes(minutes) ?? '$minutes min.';
      }
    }

    return PopupMenuButton<Duration?>(
      tooltip: localizations?.setSleepTimerTooltip ?? 'Set sleep timer',
      onSelected: (duration) async {
        if (duration == null) {
          await _sleepTimerService.cancelTimer();
        } else if (duration == const Duration(seconds: -1)) {
          // Special value for "at end of chapter"
          await _sleepTimerService.startTimerAtEndOfChapter(() async {
            if (!mounted) return;

            // CRITICAL: Check if player is initializing - if so, don't exit
            try {
              final isInitializing =
                  await _playerLifecycleChannel.invokeMethod<bool>(
                'isPlayerInitializing',
              );
              if (isInitializing ?? false) {
                await _logger.log(
                  level: 'warning',
                  subsystem: 'player',
                  message:
                      'Sleep timer at end of chapter expired but player is initializing, ignoring exit request',
                );
                return;
              }
            } on Exception catch (e) {
              await _logger.log(
                level: 'warning',
                subsystem: 'player',
                message:
                    'Failed to check player initialization state, proceeding with caution',
                cause: e.toString(),
              );
            }

            // Check if player is actually playing - don't exit if not playing
            final currentState = ref.read(playerStateProvider);
            if (currentState.playbackState == 0 || !currentState.isPlaying) {
              await _logger.log(
                level: 'info',
                subsystem: 'player',
                message:
                    'Sleep timer at end of chapter expired but player is not playing, skipping exit',
              );
              return;
            }

            await _logger.log(
              level: 'info',
              subsystem: 'player',
              message:
                  'Sleep timer at end of chapter expired, stopping playback and exiting app',
            );

            // Stop playback
            await ref.read(playerStateProvider.notifier).stop();

            // Check mounted again after async operation
            if (!mounted) return;

            // Show notification about app exit
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(localizations?.sleepTimerAppWillExit ??
                    'Sleep timer: App will exit'),
                duration: const Duration(seconds: 2),
              ),
            );

            // Stop service and exit app
            try {
              await ref.read(playerStateProvider.notifier).stopServiceAndExit();
            } on Exception catch (e) {
              // Log error but don't block UI
              await _logger.log(
                level: 'error',
                subsystem: 'player',
                message: 'Failed to stop service and exit',
                cause: e.toString(),
              );
            }
          });
        } else {
          await _sleepTimerService.startTimer(duration, () async {
            if (!mounted) return;

            // CRITICAL: Check if player is initializing - if so, don't exit
            // This prevents app exit during initialization which causes white screen
            try {
              final isInitializing =
                  await _playerLifecycleChannel.invokeMethod<bool>(
                'isPlayerInitializing',
              );
              if (isInitializing ?? false) {
                await _logger.log(
                  level: 'warning',
                  subsystem: 'player',
                  message:
                      'Sleep timer expired but player is initializing, ignoring exit request',
                );
                return;
              }
            } on Exception catch (e) {
              // If check fails, log but continue - better to be safe
              await _logger.log(
                level: 'warning',
                subsystem: 'player',
                message:
                    'Failed to check player initialization state, proceeding with caution',
                cause: e.toString(),
              );
            }

            // Check if player is actually playing - don't exit if not playing
            final currentState = ref.read(playerStateProvider);
            if (currentState.playbackState == 0 || !currentState.isPlaying) {
              await _logger.log(
                level: 'info',
                subsystem: 'player',
                message:
                    'Sleep timer expired but player is not playing, skipping exit',
              );
              return;
            }

            await _logger.log(
              level: 'info',
              subsystem: 'player',
              message: 'Sleep timer expired, stopping playback and exiting app',
            );

            // Stop playback
            await ref.read(playerStateProvider.notifier).stop();

            // Check mounted again after async operation
            if (!mounted) return;

            // Show notification about app exit
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(localizations?.sleepTimerAppWillExit ??
                    'Sleep timer: App will exit'),
                duration: const Duration(seconds: 2),
              ),
            );

            // Stop service and exit app
            try {
              await ref.read(playerStateProvider.notifier).stopServiceAndExit();
            } on Exception catch (e) {
              // Log error but don't block UI
              await _logger.log(
                level: 'error',
                subsystem: 'player',
                message: 'Failed to stop service and exit',
                cause: e.toString(),
              );
            }
          });
        }
        if (mounted) {
          setState(() {});
          _startSleepTimerUpdates();
          // Update saved state with sleep timer
          final remainingSeconds = _sleepTimerService.remainingSeconds;
          await ref.read(playerStateProvider.notifier).updateSavedStateSettings(
                sleepTimerRemainingSeconds: remainingSeconds,
              );
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem<Duration?>(
          value: const Duration(minutes: 10),
          child: Text(localizations?.sleepTimerMinutes(10) ?? '10 min.'),
        ),
        PopupMenuItem<Duration?>(
          value: const Duration(minutes: 15),
          child: Text(localizations?.sleepTimerMinutes(15) ?? '15 min.'),
        ),
        PopupMenuItem<Duration?>(
          value: const Duration(minutes: 30),
          child: Text(localizations?.sleepTimerMinutes(30) ?? '30 min.'),
        ),
        PopupMenuItem<Duration?>(
          value: const Duration(minutes: 45),
          child: Text(localizations?.sleepTimerMinutes(45) ?? '45 min.'),
        ),
        PopupMenuItem<Duration?>(
          value: const Duration(hours: 1),
          child: Text(localizations?.sleepTimerHour ?? '1 hour'),
        ),
        PopupMenuItem<Duration?>(
          value: const Duration(seconds: -1), // Special value
          child:
              Text(localizations?.atEndOfChapterLabel ?? 'At end of chapter'),
        ),
      ],
      child: Chip(
        avatar: Icon(
          isActive ? Icons.timer : Icons.timer_outlined,
          size: 18,
        ),
        label: Text(displayText),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        backgroundColor: isActive
            ? Theme.of(context).primaryColor.withValues(alpha: 0.1)
            : null,
      ),
    );
  }

  /// Starts periodic updates for sleep timer display.
  void _startSleepTimerUpdates() {
    _stopSleepTimerUpdates();
    _sleepTimerUpdateTimer = Timer.periodic(
      const Duration(seconds: 1),
      (_) {
        if (mounted && _sleepTimerService.isActive) {
          setState(() {});
        } else {
          _stopSleepTimerUpdates();
        }
      },
    );
  }

  /// Stops sleep timer updates.
  void _stopSleepTimerUpdates() {
    _sleepTimerUpdateTimer?.cancel();
    _sleepTimerUpdateTimer = null;
  }

  @override
  void dispose() {
    // CRITICAL: Clear player initializing flag on dispose
    // This ensures flag is reset if screen is disposed during initialization
    try {
      _playerLifecycleChannel.invokeMethod<bool>(
        'setPlayerInitializing',
        {'isInitializing': false},
      );
    } on Exception catch (e) {
      // Log but don't block - this is cleanup
      debugPrint('Failed to clear player initializing flag on dispose: $e');
    }

    _stopSleepTimerUpdates();
    _sleepTimerService.dispose();
    super.dispose();
  }

  String _formatDuration(Duration duration) {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    } else {
      return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
  }
}
