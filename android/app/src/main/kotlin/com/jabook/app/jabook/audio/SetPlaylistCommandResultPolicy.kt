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

import android.os.Bundle
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult

internal object SetPlaylistCommandResultPolicy {
    const val EXTRA_ERROR_REASON: String = "setPlaylistErrorReason"

    private const val ERROR_REASON_BAD_VALUE: String = "bad_value"
    private const val ERROR_REASON_TIMEOUT: String = "timeout"
    private const val ERROR_REASON_CALLBACK_FAILED: String = "callback_failed"
    private const val ERROR_REASON_EXCEPTION: String = "exception"

    fun success(): SessionResult = SessionResult(SessionResult.RESULT_SUCCESS)

    fun badValue(): SessionResult =
        SessionResult(
            SessionError.ERROR_BAD_VALUE,
            errorExtras(ERROR_REASON_BAD_VALUE),
        )

    fun timeout(): SessionResult =
        SessionResult(
            SessionError.ERROR_UNKNOWN,
            errorExtras(ERROR_REASON_TIMEOUT),
        )

    fun callbackFailed(): SessionResult =
        SessionResult(
            SessionError.ERROR_UNKNOWN,
            errorExtras(ERROR_REASON_CALLBACK_FAILED),
        )

    fun exception(exception: Throwable): SessionResult =
        SessionResult(
            SessionError.ERROR_UNKNOWN,
            errorExtras(
                "$ERROR_REASON_EXCEPTION:${exception::class.java.simpleName}",
            ),
        )

    private fun errorExtras(reason: String): Bundle =
        Bundle().apply {
            putString(EXTRA_ERROR_REASON, reason)
        }
}
