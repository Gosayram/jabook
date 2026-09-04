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

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val bookmarkNoteSheetLogger by lazy { LoggerFactoryImpl().get("BookmarkNoteSheet") }

private fun releaseBookmarkAudioPlayer(player: MediaPlayer?) {
    player?.runCatching {
        stop()
        reset()
        release()
    }
}

/**
 * Bookmark note editor sheet with optional voice-note recording/preview. Recording and playback
 * state is internal; the note text and pending audio path are hoisted to the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkNoteSheet(
    bookmarkId: String,
    note: String,
    onNoteChange: (String) -> Unit,
    audioPath: String?,
    onAudioPathChange: (String?) -> Unit,
    hasRecordAudioPermission: Boolean,
    onRequestRecordAudioPermission: () -> Unit,
    onSave: (note: String, audioPath: String?) -> Unit,
    onDismiss: () -> Unit,
    onError: (message: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceNoteRecorder = rememberVoiceNoteRecorder()
    val bookmarkPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecordingBookmark by remember { mutableStateOf(false) }
    var isPlayingBookmarkAudio by remember { mutableStateOf(false) }
    var bookmarkRecordTimeoutJob by remember { mutableStateOf<Job?>(null) }

    // Release recorder/player when the sheet leaves composition (#40)
    DisposableEffect(voiceNoteRecorder, bookmarkPlayer) {
        onDispose {
            bookmarkRecordTimeoutJob?.cancel()
            bookmarkRecordTimeoutJob = null
            voiceNoteRecorder.release()
            releaseBookmarkAudioPlayer(bookmarkPlayer.value)
            bookmarkPlayer.value = null
        }
    }

    fun stopRecordingAndPlayback() {
        bookmarkRecordTimeoutJob?.cancel()
        bookmarkRecordTimeoutJob = null
        voiceNoteRecorder.release()
        releaseBookmarkAudioPlayer(bookmarkPlayer.value)
        bookmarkPlayer.value = null
        isRecordingBookmark = false
        isPlayingBookmarkAudio = false
    }

    JabookModalBottomSheet(
        modifier = modifier,
        onDismissRequest = {
            stopRecordingAndPlayback()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.bookmarkNoteSheetTitle),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.bookmarkNoteSheetLabel)) },
                placeholder = { Text(stringResource(R.string.bookmarkNoteSheetPlaceholder)) },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                FilledTonalButton(
                    onClick = {
                        if (!hasRecordAudioPermission) {
                            onRequestRecordAudioPermission()
                            return@FilledTonalButton
                        }
                        if (isRecordingBookmark) {
                            bookmarkRecordTimeoutJob?.cancel()
                            bookmarkRecordTimeoutJob = null
                            val stopped = voiceNoteRecorder.stop()
                            isRecordingBookmark = false
                            if (!stopped) {
                                discardBookmarkVoiceNote(context.filesDir, audioPath)
                                onAudioPathChange(null)
                                onError(context.getString(R.string.errorRecordingVoiceNote))
                            }
                            return@FilledTonalButton
                        }

                        discardBookmarkVoiceNote(context.filesDir, audioPath)
                        onAudioPathChange(null)
                        val outputDir = bookmarkVoiceNoteDirectory(context.filesDir)
                        if (!outputDir.exists() && !outputDir.mkdirs()) {
                            onError(context.getString(R.string.errorRecordingVoiceNote))
                            return@FilledTonalButton
                        }
                        val outputFile = File(outputDir, "bookmark_${bookmarkId}_${UUID.randomUUID()}.m4a")
                        if (voiceNoteRecorder.start(outputFile, context)) {
                            onAudioPathChange(outputFile.absolutePath)
                            isRecordingBookmark = true
                            bookmarkRecordTimeoutJob?.cancel()
                            bookmarkRecordTimeoutJob =
                                scope.launch {
                                    delay(30_000L)
                                    if (isRecordingBookmark) {
                                        voiceNoteRecorder.stop()
                                        isRecordingBookmark = false
                                    }
                                }
                        } else {
                            outputFile.delete()
                            onError(context.getString(R.string.errorRecordingVoiceNote))
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isRecordingBookmark) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text =
                            if (isRecordingBookmark) {
                                stringResource(R.string.stopRecording)
                            } else {
                                stringResource(R.string.recordVoiceNote)
                            },
                    )
                }

                FilledTonalButton(
                    enabled = !audioPath.isNullOrBlank(),
                    onClick = {
                        val path = audioPath ?: return@FilledTonalButton
                        if (bookmarkPlayer.value != null) {
                            releaseBookmarkAudioPlayer(bookmarkPlayer.value)
                            bookmarkPlayer.value = null
                            isPlayingBookmarkAudio = false
                            return@FilledTonalButton
                        }
                        val player = MediaPlayer()
                        bookmarkPlayer.value = player
                        try {
                            player.setDataSource(path)
                            player.setOnCompletionListener {
                                bookmarkPlayer.value?.runCatching {
                                    reset()
                                    release()
                                }
                                bookmarkPlayer.value = null
                                isPlayingBookmarkAudio = false
                            }
                            player.setOnPreparedListener {
                                if (bookmarkPlayer.value !== it) return@setOnPreparedListener
                                it.start()
                                isPlayingBookmarkAudio = true
                            }
                            player.setOnErrorListener { _, what, extra ->
                                bookmarkNoteSheetLogger.e {
                                    "Bookmark voice-note playback failed in MediaPlayer listener: what=$what extra=$extra"
                                }
                                bookmarkPlayer.value = null
                                isPlayingBookmarkAudio = false
                                scope.launch {
                                    onError(context.getString(R.string.errorPlayingVoiceNote))
                                }
                                player.runCatching {
                                    reset()
                                    release()
                                }
                                true
                            }
                            player.prepareAsync()
                        } catch (e: java.io.IOException) {
                            bookmarkNoteSheetLogger.e(e) { "Failed to prepare bookmark voice-note (I/O)" }
                            scope.launch {
                                onError(context.getString(R.string.errorPlayingVoiceNote))
                            }
                            player.runCatching {
                                reset()
                                release()
                            }
                            bookmarkPlayer.value = null
                            isPlayingBookmarkAudio = false
                        } catch (e: IllegalStateException) {
                            bookmarkNoteSheetLogger.e(e) { "Failed to prepare bookmark voice-note (illegal state)" }
                            scope.launch {
                                onError(context.getString(R.string.errorPlayingVoiceNote))
                            }
                            player.runCatching {
                                reset()
                                release()
                            }
                            bookmarkPlayer.value = null
                            isPlayingBookmarkAudio = false
                        } catch (e: SecurityException) {
                            bookmarkNoteSheetLogger.e(e) { "Failed to prepare bookmark voice-note (security)" }
                            scope.launch {
                                onError(context.getString(R.string.errorPlayingVoiceNote))
                            }
                            player.runCatching {
                                reset()
                                release()
                            }
                            bookmarkPlayer.value = null
                            isPlayingBookmarkAudio = false
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isPlayingBookmarkAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription =
                            if (isPlayingBookmarkAudio) {
                                stringResource(R.string.stopPlayback)
                            } else {
                                stringResource(R.string.playVoiceNote)
                            },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text =
                            if (isPlayingBookmarkAudio) {
                                stringResource(R.string.stopPlayback)
                            } else {
                                stringResource(R.string.playVoiceNote)
                            },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                FilledTonalButton(
                    onClick = {
                        stopRecordingAndPlayback()
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.skip))
                }
                FilledTonalButton(
                    onClick = {
                        stopRecordingAndPlayback()
                        onSave(note, audioPath)
                    },
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        }
    }
}
