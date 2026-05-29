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

/**
 * Analyzes audio to find sentence boundaries for Smart Resume.
 *
 * When resuming after a long pause, instead of rewinding a fixed amount,
 * this analyzer finds the nearest sentence start so the user hears
 * a complete sentence.
 */
public interface SpeechSegmentAnalyzer {
    /**
     * Finds the start of the last sentence before [positionMs].
     *
     * @param bookId Book identifier
     * @param positionMs Current playback position
     * @param lookbackMs How far back to look for sentence boundaries
     * @return Position in ms of the last sentence start
     */
    public fun findLastSentenceStart(
        bookId: String,
        positionMs: Long,
        lookbackMs: Long,
    ): Long
}
