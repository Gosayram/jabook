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

import 'package:flutter/material.dart';

/// Widget for player progress (slider and time display).
///
/// This is a pure UI widget that handles slider interactions.
/// All business logic should be handled by the parent widget or provider.
class PlayerProgress extends StatefulWidget {
  /// Creates a new PlayerProgress instance.
  const PlayerProgress({
    super.key,
    required this.currentPosition,
    required this.duration,
    required this.onSeek,
    this.isDragging = false,
    this.sliderValue,
  });

  /// Current playback position in milliseconds.
  final int currentPosition;

  /// Total duration in milliseconds.
  final int duration;

  /// Callback when user seeks to a new position.
  final ValueChanged<int> onSeek;

  /// Whether slider is currently being dragged.
  final bool isDragging;

  /// Current slider value (0.0 to 1.0) when dragging.
  final double? sliderValue;

  @override
  State<PlayerProgress> createState() => _PlayerProgressState();
}

class _PlayerProgressState extends State<PlayerProgress> {
  double? _localSliderValue;
  bool _isDragging = false;

  @override
  void didUpdateWidget(PlayerProgress oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_isDragging && widget.sliderValue != oldWidget.sliderValue) {
      _localSliderValue = widget.sliderValue;
    }
  }

  void _onSliderChanged(double value) {
    setState(() {
      _localSliderValue = value;
      _isDragging = true;
    });
  }

  void _onSliderStart(double value) {
    setState(() {
      _localSliderValue = value;
      _isDragging = true;
    });
  }

  void _onSliderEnd(double value) {
    final newPosition = (value * widget.duration).round();
    widget.onSeek(newPosition);
    setState(() {
      _isDragging = false;
      _localSliderValue = null;
    });
  }

  String _formatDuration(Duration duration) {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final effectiveValue = _isDragging && _localSliderValue != null
        ? _localSliderValue!.clamp(0.0, 1.0)
        : (widget.duration > 0
            ? widget.currentPosition / widget.duration
            : 0.0);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(_formatDuration(
                  Duration(milliseconds: widget.currentPosition))),
              Text(_formatDuration(Duration(milliseconds: widget.duration))),
            ],
          ),
          const SizedBox(height: 8),
          Slider(
            value: effectiveValue,
            onChanged: widget.duration > 0 ? _onSliderChanged : null,
            onChangeStart: widget.duration > 0 ? _onSliderStart : null,
            onChangeEnd: widget.duration > 0 ? _onSliderEnd : null,
          ),
        ],
      ),
    );
  }
}
