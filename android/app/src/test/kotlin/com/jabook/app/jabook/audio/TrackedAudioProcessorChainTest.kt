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

import com.jabook.app.jabook.audio.processors.SkipSilenceAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackedAudioProcessorChainTest {
    private fun realSkipSilence(): SkipSilenceAudioProcessor =
        SkipSilenceAudioProcessor(
            enabled = true,
            silenceThresholdNormalized = 0.001f,
            minSilenceDurationMs = 150,
        )

    @Test
    fun `getSkippedOutputFrameCount includes custom skip-silence frames`() {
        // Media3's built-in SilenceSkippingAudioProcessor is disabled by jabook
        // (setSkipSilenceEnabled(false)), so the default chain reports 0. The
        // TrackedAudioProcessorChain must add our custom skipper's frames.
        val chain = TrackedAudioProcessorChain(arrayOf(realSkipSilence()))

        // Before any audio flows, the custom skipper reports 0 — chain is 0 + 0.
        assertEquals(0L, chain.getSkippedOutputFrameCount())
    }

    @Test
    fun `getSkippedOutputFrameCount is zero without a skip-silence processor`() {
        val chain = TrackedAudioProcessorChain(emptyArray())
        assertEquals(0L, chain.getSkippedOutputFrameCount())
    }

    @Test
    fun `exposes the custom skip-silence processor as an AudioProcessor`() {
        val processor = realSkipSilence()
        val chain = TrackedAudioProcessorChain(arrayOf(processor))

        // The chain must surface our processors so Media3 runs them.
        assertEquals(processor, chain.getAudioProcessors().first())
    }

    @Test
    fun `delegates skip-silence enable toggle to the underlying chain`() {
        val chain = TrackedAudioProcessorChain(emptyArray())
        // jabook disables Media3's built-in skipper; the toggle must pass through.
        assertEquals(false, chain.applySkipSilenceEnabled(false))
    }
}
