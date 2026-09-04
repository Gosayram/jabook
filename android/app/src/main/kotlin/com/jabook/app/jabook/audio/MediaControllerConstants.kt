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

import java.util.concurrent.TimeUnit

/**
 * Constants for MediaController timeouts.
 *
 * Different contexts require different timeout values:
 * - Widget updates need faster timeouts (1s) for better UX
 * - Regular operations use default timeout (2s)
 * - Quick fallback operations use very short timeout (500ms) for fast fallback
 */
public object MediaControllerConstants {
    /**
     * Default timeout for MediaController operations (2 seconds).
     * Used for regular operations like initialization in controllers.
     */
    public const val DEFAULT_TIMEOUT_SECONDS: Int = 2

    /**
     * Timeout for widget updates (1 second).
     * Widgets need faster timeouts for better user experience.
     */
    public const val WIDGET_TIMEOUT_SECONDS: Int = 1

    /**
     * Quick fallback timeout (500 milliseconds).
     * Used when we want to quickly fallback to alternative methods if MediaController is slow.
     */
    public const val QUICK_FALLBACK_TIMEOUT_MS: Int = 500

    /**
     * Gets default timeout in TimeUnit.SECONDS.
     */
    @JvmStatic
    public fun getDefaultTimeout(timeUnit: TimeUnit): Long = timeUnit.convert(DEFAULT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)

    /**
     * Gets widget timeout in TimeUnit.SECONDS.
     */
    @JvmStatic
    public fun getWidgetTimeout(timeUnit: TimeUnit): Long = timeUnit.convert(WIDGET_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)

    /**
     * Gets quick fallback timeout in TimeUnit.MILLISECONDS.
     */
    @JvmStatic
    public fun getQuickFallbackTimeout(timeUnit: TimeUnit): Long =
        timeUnit.convert(QUICK_FALLBACK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
}
