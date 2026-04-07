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

package com.jabook.app.jabook.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Centralized runtime policy for network dispatching and timeouts.
 *
 * Keeps timeout values aligned across:
 * - main API stack (NetworkModule),
 * - mirror health checks (MirrorManager),
 * - audio remote media network path (PlaylistManager).
 */
public object NetworkRuntimePolicy {
    public val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Main API client policy
    public const val API_CALL_TIMEOUT_SECONDS: Long = 90L
    public const val API_CONNECT_TIMEOUT_SECONDS: Long = 15L
    public const val API_READ_TIMEOUT_SECONDS: Long = 45L
    public const val API_WRITE_TIMEOUT_SECONDS: Long = 15L

    // Mirror health checks should fail fast
    public const val MIRROR_HEALTH_CALL_TIMEOUT_SECONDS: Long = 6L
    public const val MIRROR_HEALTH_CONNECT_TIMEOUT_SECONDS: Long = 5L
    public const val MIRROR_HEALTH_READ_TIMEOUT_SECONDS: Long = 5L
    public const val MIRROR_HEALTH_WRITE_TIMEOUT_SECONDS: Long = 5L

    // Audio remote media path
    public const val AUDIO_MEDIA_CONNECT_TIMEOUT_SECONDS: Long = 30L
    public const val AUDIO_MEDIA_READ_TIMEOUT_SECONDS: Long = 30L
    public const val AUDIO_MEDIA_WRITE_TIMEOUT_SECONDS: Long = 30L
}
