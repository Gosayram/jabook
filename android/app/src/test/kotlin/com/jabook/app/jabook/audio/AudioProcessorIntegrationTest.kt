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

import com.google.common.truth.Truth.assertThat
import com.jabook.app.jabook.audio.processors.AudioProcessorChainManager
import com.jabook.app.jabook.audio.processors.ProxyAudioProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.OptIn
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AudioProcessorIntegrationTest {
    private lateinit var manager: AudioProcessorChainManager

    @Before
    public fun setUp() {
        manager = AudioProcessorChainManager()
    }

    @Test
    public fun proxies_returnsStableListOfProxies() =
        runBlocking {
            val proxies1 = manager.proxies()
            val proxies2 = manager.proxies()

            assertThat(proxies1).isSameInstanceAs(proxies2)
            assertThat(proxies1).hasSize(9)
        }

    @Test
    public fun applySettings_normalizationEnabled_setsLoudnessNormalizer() =
        runBlocking {
            val settings = AudioProcessingSettings(normalizeVolume = true)
            manager.applySettings(settings)

            val loudnessProxy = manager.loudnessProxy()
            assertThat(loudnessProxy.delegate).isInstanceOf(LoudnessNormalizer::class.java)
        }

    @Test
    public fun applySettings_normalizationDisabled_usesPassthrough() =
        runBlocking {
            val settings = AudioProcessingSettings(normalizeVolume = false)
            manager.applySettings(settings)

            val loudnessProxy = manager.loudnessProxy()
            assertThat(loudnessProxy.delegate).isInstanceOf(ProxyAudioProcessor.PassthroughAudioProcessor::class.java)
        }

    @Test
    public fun applySettings_volumeBoostLevel_setsVolumeBoostProcessor() =
        runBlocking {
            val settings = AudioProcessingSettings(volumeBoostLevel = VolumeBoostLevel.High)
            manager.applySettings(settings)

            val boostProxy = manager.boostProxy()
            assertThat(boostProxy.delegate).isInstanceOf(VolumeBoostProcessor::class.java)
        }

    @Test
    public fun applySettings_drcLevel_setsDynamicRangeCompressor() =
        runBlocking {
            val settings = AudioProcessingSettings(drcLevel = DRCLevel.High)
            manager.applySettings(settings)

            val drcProxy = manager.drcProxy()
            assertThat(drcProxy.delegate).isInstanceOf(DynamicRangeCompressor::class.java)
        }

    @Test
    public fun applySettings_speechEnhancer_setsSpeechEnhancer() =
        runBlocking {
            val settings = AudioProcessingSettings(speechEnhancer = true)
            manager.applySettings(settings)

            val speechProxy = manager.speechProxy()
            assertThat(speechProxy.delegate).isInstanceOf(SpeechEnhancer::class.java)
        }

    @Test
    public fun applySettings_autoVolumeLeveling_setsAutoVolumeLeveler() =
        runBlocking {
            val settings = AudioProcessingSettings(autoVolumeLeveling = true)
            manager.applySettings(settings)

            val levelerProxy = manager.levelerProxy()
            assertThat(levelerProxy.delegate).isInstanceOf(AutoVolumeLeveler::class.java)
        }

    @Test
    public fun applySettings_skipSilence_setsSkipSilenceProcessor() =
        runBlocking {
            val settings = AudioProcessingSettings(skipSilence = true)
            manager.applySettings(settings)

            val skipSilenceProxy = manager.skipSilenceProxy()
            assertThat(skipSilenceProxy.delegate).isInstanceOf(SkipSilenceAudioProcessor::class.java)
        }

    @Test
    public fun applySettings_echoEnabled_setsEchoProcessor() =
        runBlocking {
            val settings = AudioProcessingSettings(echoEnabled = true)
            manager.applySettings(settings)

            val echoProxy = manager.echoProxy()
            assertThat(echoProxy.delegate).isInstanceOf(EchoAudioProcessor::class.java)
        }
}
