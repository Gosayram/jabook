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

package com.jabook.app.jabook.compose.feature.player

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.io.File

/**
 * Holds [MediaRecorder] state for bookmark voice notes. All MediaRecorder calls are guarded with
 * runCatching so a failed stop never leaks the recorder or skips reset/release (unified guard).
 */
internal class VoiceNoteRecorder {
    private var recorder: MediaRecorder? = null

    val isActive: Boolean
        get() = recorder != null

    /**
     * Configures and starts recording to [outputFile]. Returns false when setup fails; the caller
     * owns deleting [outputFile] in that case.
     */
    fun start(
        outputFile: File,
        context: Context,
    ): Boolean {
        if (recorder != null) return false
        val newRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        val started =
            runCatching {
                newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                newRecorder.setAudioEncodingBitRate(96_000)
                newRecorder.setAudioSamplingRate(44_100)
                newRecorder.setOutputFile(outputFile.absolutePath)
                newRecorder.prepare()
                newRecorder.start()
            }.isSuccess
        if (started) {
            recorder = newRecorder
            return true
        }
        newRecorder.runCatching { reset() }
        newRecorder.runCatching { release() }
        return false
    }

    /**
     * Stops the active recording and releases the recorder. Returns false when stop failed (the
     * output file may be unusable). Idempotent: returns true when nothing was recording.
     */
    fun stop(): Boolean {
        val current = recorder ?: return true
        val stopped = runCatching { current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
        recorder = null
        return stopped.isSuccess
    }

    /** Hard release (dismiss/save/timeout/composition exit). Idempotent; stop failures ignored. */
    fun release() {
        val current = recorder ?: return
        runCatching { current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
        recorder = null
    }
}

@Composable
internal fun rememberVoiceNoteRecorder(): VoiceNoteRecorder {
    val voiceNoteRecorder = remember { VoiceNoteRecorder() }
    DisposableEffect(voiceNoteRecorder) {
        onDispose { voiceNoteRecorder.release() }
    }
    return voiceNoteRecorder
}

internal fun bookmarkVoiceNoteDirectory(filesDir: File): File = File(filesDir, "bookmark_notes")

internal fun discardBookmarkVoiceNote(
    filesDir: File,
    path: String?,
) {
    if (path.isNullOrBlank()) return
    val directory = bookmarkVoiceNoteDirectory(filesDir)
    val file = File(path)
    if (runCatching { file.parentFile?.canonicalFile == directory.canonicalFile }.getOrDefault(false)) {
        file.delete()
    }
}
