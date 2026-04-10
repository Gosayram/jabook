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

import android.os.Trace

/**
 * Small helper for app-level trace sections visible in Perfetto / Android Studio.
 *
 * Keep section names short (system limit is 127 chars).
 */
public object PerfTrace {
    public inline fun <T> section(
        name: String,
        block: () -> T,
    ): T {
        Trace.beginSection(name.take(127))
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }
}
