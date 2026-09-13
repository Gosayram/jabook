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

package com.jabook.app.jabook.compose.core.util

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private fun isReduceMotionEnabled(context: android.content.Context): Boolean =
    runCatching {
        val cr = context.contentResolver
        val transition = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val window = Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        val animator = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        transition == 0f || window == 0f || animator == 0f
    }.getOrDefault(false)

/**
 * Returns true when any system animation scale is disabled (0x), which implies reduced motion.
 *
 * Checks all three [Settings.Global] scales: TRANSITION, WINDOW, ANIMATOR_DURATION_SCALE.
 * Reactive via [ContentObserver] — updates live when user toggles Animator duration in Developer options.
 * ponytail: ContentObserver with pre-S Handler(Looper.getMainLooper()); WATCHES need live observer — polling alone misses toggle without recomposition.
 */
@Composable
public fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduceMotion by remember { mutableStateOf(isReduceMotionEnabled(context)) }

    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduceMotion = isReduceMotionEnabled(context)
                }
            }
        val cr = context.contentResolver
        val uris =
            listOf(
                Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
                Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE),
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            )
        uris.forEach { uri -> cr.registerContentObserver(uri, false, observer) }
        reduceMotion = isReduceMotionEnabled(context)
        onDispose { cr.unregisterContentObserver(observer) }
    }
    return reduceMotion
}
