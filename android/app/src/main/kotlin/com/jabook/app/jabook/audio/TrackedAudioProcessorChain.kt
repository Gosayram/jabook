// Copyright 2026 Jabook Contributors
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

package com.jabook.app.jabook.audio

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import com.jabook.app.jabook.audio.processors.SkipSilenceAudioProcessor

/**
 * [AudioProcessorChain] that delegates to Media3's [DefaultAudioProcessorChain]
 * but additionally reports our custom [SkipSilenceAudioProcessor]'s net skipped
 * frames in [getSkippedOutputFrameCount].
 *
 * Media3's position tracking (seek targets, bookmarks, resume position, the
 * notification progress bar) compensates skipped audio ONLY through
 * `getSkippedOutputFrameCount()`. jabook disables the built-in
 * `SilenceSkippingAudioProcessor` (`setSkipSilenceEnabled(false)`) and runs its
 * own skipper instead, so without this chain the media position drifts ahead by
 * every skipped-silence frame over a long audiobook.
 */
public class TrackedAudioProcessorChain(
    userProcessors: Array<AudioProcessor>,
) : AudioProcessorChain {
    private val defaultChain = DefaultAudioProcessorChain(*userProcessors)
    private val customSkipSilence: SkipSilenceAudioProcessor? =
        userProcessors.filterIsInstance<SkipSilenceAudioProcessor>().firstOrNull()

    override fun getAudioProcessors(): Array<AudioProcessor> = defaultChain.audioProcessors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        defaultChain.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = defaultChain.applySkipSilenceEnabled(skipSilenceEnabled)

    override fun getMediaDuration(playoutDuration: Long): Long = defaultChain.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long =
        // Media3's built-in skipper is disabled by jabook, so the default chain
        // reports 0 — add our custom skipper's net skipped frames.
        defaultChain.getSkippedOutputFrameCount() + (customSkipSilence?.getSkippedFrames() ?: 0L)
}
