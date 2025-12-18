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

package com.jabook.app.jabook.audio

import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes

/**
 * Utility class for managing notification icons.
 *
 * Provides consistent icon handling for media notifications with
 * support for vector drawables, bitmap icons, and theming.
 *
 * This addresses the issue where Jabook currently uses system icons
 * (android.R.drawable.*) which may not look good on all devices.
 */
object NotificationIconProvider {
    /**
     * Creates an Icon from a drawable resource.
     * Supports both vector and bitmap drawables.
     *
     * @param context Application context
     * @param resId Drawable resource ID
     * @return Icon instance for notification actions
     */
    fun createIcon(
        context: Context,
        @DrawableRes resId: Int,
    ): Icon = Icon.createWithResource(context, resId)

    /**
     * Gets the appropriate play icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for play icon
     */
    @DrawableRes
    fun getPlayIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        // For now, use system icon
        return android.R.drawable.ic_media_play
    }

    /**
     * Gets the appropriate pause icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for pause icon
     */
    @DrawableRes
    fun getPauseIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        return android.R.drawable.ic_media_pause
    }

    /**
     * Gets the appropriate next icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for next icon
     */
    @DrawableRes
    fun getNextIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        return android.R.drawable.ic_media_next
    }

    /**
     * Gets the appropriate previous icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for previous icon
     */
    @DrawableRes
    fun getPreviousIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        return android.R.drawable.ic_media_previous
    }

    /**
     * Gets the appropriate rewind icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for rewind icon
     */
    @DrawableRes
    fun getRewindIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        return android.R.drawable.ic_media_rew
    }

    /**
     * Gets the appropriate forward icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for forward icon
     */
    @DrawableRes
    fun getForwardIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        return android.R.drawable.ic_media_ff
    }

    /**
     * Gets the appropriate stop icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for stop icon
     */
    @DrawableRes
    fun getStopIcon(context: Context): Int {
        // TODO: Replace with custom drawable when design is ready
        return android.R.drawable.ic_menu_close_clear_cancel
    }

    /**
     * Applies theme-based tinting to notification icons if needed.
     *
     * This method can be extended in the future to support dynamic theming
     * based on Material You colors or app theme.
     *
     * @param context Application context
     * @param icon Icon to tint
     * @return Tinted icon (currently returns same icon, to be implemented)
     */
    fun applyTheming(
        context: Context,
        icon: Icon,
    ): Icon {
        // TODO: Implement dynamic theming when Material You support is added
        // For now, return icon as-is
        return icon
    }
}
