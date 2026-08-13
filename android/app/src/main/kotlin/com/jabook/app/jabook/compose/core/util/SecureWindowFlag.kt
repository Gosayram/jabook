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

import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap

internal class SecureFlagLeaseCounter<K>(
    private val onFirstAcquire: (K) -> Unit,
    private val onFinalRelease: (K) -> Unit,
    private val counts: MutableMap<K, Int> = mutableMapOf(),
) {
    fun acquire(key: K): () -> Unit {
        val count = counts[key] ?: 0
        if (count == 0) onFirstAcquire(key)
        counts[key] = count + 1

        var released = false
        return {
            if (!released) {
                released = true
                release(key)
            }
        }
    }

    private fun release(key: K) {
        val count = counts[key] ?: return
        if (count == 1) {
            counts.remove(key)
            onFinalRelease(key)
        } else {
            counts[key] = count - 1
        }
    }
}

/** Shares [WindowManager.LayoutParams.FLAG_SECURE] ownership between overlapping screens. */
internal object SecureWindowFlag {
    private val lock = Any()
    private val counter =
        SecureFlagLeaseCounter<Window>(
            onFirstAcquire = { it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) },
            onFinalRelease = { it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) },
            counts = WeakHashMap(),
        )

    fun acquire(window: Window): () -> Unit {
        val release = synchronized(lock) { counter.acquire(window) }
        return { synchronized(lock) { release() } }
    }
}
