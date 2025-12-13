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

package com.jabook.app.jabook.compose.data.download

import android.util.Log
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Stub implementation of TorrentDownloader for MVP testing.
 *
 * This simulates download progress without actually downloading files.
 * Replace with real torrent client integration in production.
 */
class StubTorrentDownloader
    @Inject
    constructor() : TorrentDownloader {
        companion object {
            private const val TAG = "StubTorrentDownloader"
            private const val TOTAL_STEPS = 100
            private const val STEP_DELAY_MS = 50L // 5 seconds total for demo
        }

        override suspend fun download(
            torrentUrl: String,
            savePath: String,
            onProgress: (Float) -> Unit,
        ): String {
            Log.d(TAG, "Starting stub download: $torrentUrl to $savePath")

            // Simulate download progress
            for (step in 0..TOTAL_STEPS) {
                val progress = step / TOTAL_STEPS.toFloat()
                onProgress(progress)
                delay(STEP_DELAY_MS)
            }

            val resultPath = "$savePath/downloaded_book.mp3"
            Log.d(TAG, "Stub download completed: $resultPath")
            return resultPath
        }

        override suspend fun pause(downloadId: String) {
            Log.d(TAG, "Stub pause: $downloadId")
            // No-op for stub
        }

        override suspend fun resume(downloadId: String) {
            Log.d(TAG, "Stub resume: $downloadId")
            // No-op for stub
        }

        override suspend fun cancel(downloadId: String) {
            Log.d(TAG, "Stub cancel: $downloadId")
            // No-op for stub
        }
    }
