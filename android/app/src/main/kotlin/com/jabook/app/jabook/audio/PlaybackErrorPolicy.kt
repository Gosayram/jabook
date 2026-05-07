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

import androidx.media3.common.PlaybackException

internal enum class PlaybackRecoveryAction {
    RETRY,
    SKIP_TRACK,
    RESCAN_LIBRARY,
    NONE,
}

internal data class PlaybackErrorResolution(
    val userMessage: String,
    val action: PlaybackRecoveryAction,
)

/**
 * Maps Media3 playback error codes to stable user-facing messages and recovery actions.
 */
internal object PlaybackErrorPolicy {
    fun resolve(
        errorCode: Int,
        hasRetriesLeft: Boolean,
        canSkipTrack: Boolean,
        fallbackMessage: String?,
    ): PlaybackErrorResolution =
        when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                if (hasRetriesLeft) {
                    PlaybackErrorResolution(
                        userMessage = "Network connection failed, retrying...",
                        action = PlaybackRecoveryAction.RETRY,
                    )
                } else {
                    PlaybackErrorResolution(
                        userMessage = "Network error: Unable to connect. Please check your internet connection.",
                        action = PlaybackRecoveryAction.NONE,
                    )
                }
            }
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                if (hasRetriesLeft) {
                    PlaybackErrorResolution(
                        userMessage = "Network timeout, retrying...",
                        action = PlaybackRecoveryAction.RETRY,
                    )
                } else {
                    PlaybackErrorResolution(
                        userMessage = "Network timeout: Connection timed out. Please try again.",
                        action = PlaybackRecoveryAction.NONE,
                    )
                }
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                PlaybackErrorResolution(
                    userMessage = "Server error: Unable to load audio from server. Please try again later.",
                    action = PlaybackRecoveryAction.NONE,
                )
            }
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                if (canSkipTrack) {
                    PlaybackErrorResolution(
                        userMessage = "File not found, skipping to next track...",
                        action = PlaybackRecoveryAction.SKIP_TRACK,
                    )
                } else {
                    PlaybackErrorResolution(
                        userMessage = "File not found: Audio file is missing or has been moved.",
                        action = PlaybackRecoveryAction.RESCAN_LIBRARY,
                    )
                }
            }
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                if (canSkipTrack) {
                    PlaybackErrorResolution(
                        userMessage = "Permission denied, skipping...",
                        action = PlaybackRecoveryAction.SKIP_TRACK,
                    )
                } else {
                    PlaybackErrorResolution(
                        userMessage = "Permission denied: Cannot access audio file.",
                        action = PlaybackRecoveryAction.NONE,
                    )
                }
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> {
                if (canSkipTrack) {
                    PlaybackErrorResolution(
                        userMessage = "Format/decoder error, skipping...",
                        action = PlaybackRecoveryAction.SKIP_TRACK,
                    )
                } else {
                    PlaybackErrorResolution(
                        userMessage = "Format error: Audio file is corrupted or in an unsupported format.",
                        action = PlaybackRecoveryAction.NONE,
                    )
                }
            }
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            -> {
                if (canSkipTrack) {
                    PlaybackErrorResolution(
                        userMessage = "Audio output error, skipping...",
                        action = PlaybackRecoveryAction.SKIP_TRACK,
                    )
                } else {
                    PlaybackErrorResolution(
                        userMessage = "Audio error: Unable to initialize audio playback.",
                        action = PlaybackRecoveryAction.NONE,
                    )
                }
            }
            else -> {
                val message = fallbackMessage ?: "Unknown error"
                PlaybackErrorResolution(
                    userMessage = "Playback error: $message (code: $errorCode)",
                    action = PlaybackRecoveryAction.NONE,
                )
            }
        }
}
