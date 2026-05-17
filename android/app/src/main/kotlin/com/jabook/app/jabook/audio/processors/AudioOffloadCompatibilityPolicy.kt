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

package com.jabook.app.jabook.audio.processors

import androidx.media3.common.util.UnstableApi

/**
 * Policy that determines whether audio offload mode is compatible
 * with the current [AudioProcessingSettings].
 *
 * Audio offload delegates audio decoding to the hardware DSP, which
 * saves battery but is **incompatible** with custom [androidx.media3.common.audio.AudioProcessor]
 * instances — the DSP cannot run user-defined processing chains.
 *
 * When any processor that uses a custom [androidx.media3.common.audio.AudioProcessor]
 * is active, offload must be disabled so the software path is used instead.
 *
 * P-05: Reactive settings — offload compatibility check.
 */
@UnstableApi
public object AudioOffloadCompatibilityPolicy {
    /**
     * Returns `true` when audio offload can safely be enabled for the
     * given [settings].
     *
     * Offload is **disabled** when any of the following is active:
     * - loudness normalisation ([AudioProcessingSettings.normalizeVolume])
     * - speech enhancement ([AudioProcessingSettings.speechEnhancer])
     * - auto volume levelling ([AudioProcessingSettings.autoVolumeLeveling])
     * - DRC compression (any level other than [DRCLevel.Off])
     * - volume boost (any level other than [VolumeBoostLevel.Off])
     * - skip silence ([AudioProcessingSettings.skipSilence])
     *
     * @param settings Current audio processing configuration.
     * @return `true` if offload is safe to enable.
     */
    public fun isOffloadCompatible(settings: AudioProcessingSettings): Boolean =
        !settings.normalizeVolume &&
            !settings.speechEnhancer &&
            !settings.autoVolumeLeveling &&
            settings.drcLevel == DRCLevel.Off &&
            settings.volumeBoostLevel == VolumeBoostLevel.Off &&
            !settings.skipSilence

    /**
     * Returns `true` when gapless playback is possible.
     *
     * Gapless and crossfade are mutually exclusive — they both manage
     * the transition between tracks.
     *
     * @param settings Current audio processing configuration.
     */
    public fun isGaplessPossible(settings: AudioProcessingSettings): Boolean = !settings.isCrossfadeEnabled
}
