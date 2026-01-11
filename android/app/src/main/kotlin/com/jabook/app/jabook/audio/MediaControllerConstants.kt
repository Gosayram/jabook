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
 * - Service initialization needs more time (5s) as it's a critical path
 * - Widget updates need faster timeouts (1s) for better UX
 * - Regular operations use default timeout (2s)
 * - Quick fallback operations use very short timeout (500ms) for fast fallback
 */
public object MediaControllerConstants {
    /**
     * Default timeout for MediaController operations (2 seconds).
     * Used for regular operations like initialization in controllers.
     */
    public const val DEFAULT_TIMEOUT_SECONDS: Int = L

    /**
     * Timeout for service initialization (5 seconds).
     * Service initialization is critical and may take longer, especially on first start.
     */
    public const val SERVICE_INIT_TIMEOUT_SECONDS: Int = L

    /**
     * Timeout for widget updates (1 second).
     * Widgets need faster timeouts for better user experience.
     */
    public const val WIDGET_TIMEOUT_SECONDS: Int = L

    /**
     * Quick fallback timeout (500 milliseconds).
     * Used when we want to quickly fallback to alternative methods if MediaController is slow.
     */
    public const val QUICK_FALLBACK_TIMEOUT_MS: Int = L

    /**
     * Gets default timeout in TimeUnit.SECONDS.
     */
    @JvmStatic
    public fun getDefaultTimeout(timeUnit: TimeUnit): Long = timeUnit.convert(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /**
     * Gets service init timeout in TimeUnit.SECONDS.
     */
    @JvmStatic
    public fun getServiceInitTimeout(timeUnit: TimeUnit): Long = timeUnit.convert(SERVICE_INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /**
     * Gets widget timeout in TimeUnit.SECONDS.
     */
    @JvmStatic
    public fun getWidgetTimeout(timeUnit: TimeUnit): Long = timeUnit.convert(WIDGET_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /**
     * Gets quick fallback timeout in TimeUnit.MILLISECONDS.
     */
    @JvmStatic
    public fun getQuickFallbackTimeout(timeUnit: TimeUnit): Long = timeUnit.convert(QUICK_FALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}
