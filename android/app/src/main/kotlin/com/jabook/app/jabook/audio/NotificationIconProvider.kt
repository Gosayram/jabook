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

import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import com.jabook.app.jabook.R

/**
 * Utility class for managing notification icons.
 *
 * Provides consistent icon handling for media notifications with
 * support for vector drawables, bitmap icons, and theming.
 *
 * This addresses the issue where Jabook currently uses system icons
 * (android.R.drawable.*) which may not look good on all devices.
 */
public object NotificationIconProvider {
    /**
     * Creates an Icon from a drawable resource.
     * Supports both vector and bitmap drawables.
     *
     * @param context Application context
     * @param resId Drawable resource ID
     * @return Icon instance for notification actions
     */
    public fun createIcon(
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
    public fun getPlayIcon(context: Context): Int = R.drawable.ic_play

    /**
     * Gets the appropriate pause icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for pause icon
     */
    @DrawableRes
    public fun getPauseIcon(context: Context): Int = R.drawable.ic_pause

    /**
     * Gets the appropriate next icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for next icon
     */
    @DrawableRes
    public fun getNextIcon(context: Context): Int = R.drawable.ic_skip_next

    /**
     * Gets the appropriate previous icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for previous icon
     */
    @DrawableRes
    public fun getPreviousIcon(context: Context): Int = R.drawable.ic_skip_previous

    /**
     * Gets the appropriate rewind icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for rewind icon
     */
    @DrawableRes
    public fun getRewindIcon(context: Context): Int = R.drawable.ic_rewind

    /**
     * Gets the appropriate forward icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for forward icon
     */
    @DrawableRes
    public fun getForwardIcon(context: Context): Int = R.drawable.ic_forward

    /**
     * Gets the appropriate stop icon based on app theme.
     *
     * @param context Application context
     * @return Resource ID for stop icon
     */
    @DrawableRes
    public fun getStopIcon(context: Context): Int = R.drawable.ic_close

    /**
     * Apply dynamic theming to notification icon based on Material You colors.
     *
     * On Android 12+ (API 31+), this extracts the primary color from the system theme
     * and applies it to the notification icon to match the user's wallpaper-based theme.
     *
     * @param context Application context
     * @param icon Icon to tint
     * @return Tinted icon with Material You colors (Android 12+) or original icon (older versions)
     */
    public fun applyTheming(
        context: Context,
        icon: Icon,
    ): Icon {
        // Material You (dynamic colors) is only available on Android 12+ (API 31+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                // Extract primary color from Material You theme
                val typedValue = android.util.TypedValue()
                val theme = context.theme

                // Get system accent color (Material You primary color)
                val colorResolved =
                    theme.resolveAttribute(
                        android.R.attr.colorPrimary,
                        typedValue,
                        true,
                    )

                if (colorResolved && typedValue.data != 0) {
                    // Create tinted icon with Material You color
                    return icon.setTint(typedValue.data)
                }
            } catch (e: Exception) {
                // Fallback to original icon if theming fails
                android.util.Log.w("NotificationIconProvider", "Failed to apply Material You theming", e)
            }
        }

        // Return original icon for Android <12 or if theming failed
        return icon
    }
}
