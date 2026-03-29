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

package com.jabook.app.jabook.core.datastore

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.jabook.app.jabook.crash.CrashDiagnostics

public object DataStoreCorruptionPolicy {
    public fun preferencesHandler(storeName: String): ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler { corruption ->
            reportCorruption(
                storeName = storeName,
                storeType = "preferences",
                throwable = corruption,
            )
            emptyPreferences()
        }

    public fun <T> protoHandler(
        storeName: String,
        defaultValue: T,
    ): ReplaceFileCorruptionHandler<T> =
        ReplaceFileCorruptionHandler { corruption ->
            reportCorruption(
                storeName = storeName,
                storeType = "proto",
                throwable = corruption,
            )
            defaultValue
        }

    private fun reportCorruption(
        storeName: String,
        storeType: String,
        throwable: Throwable,
    ) {
        CrashDiagnostics.reportNonFatal(
            tag = "datastore_corruption_recovered",
            throwable = throwable,
            attributes =
                mapOf(
                    "store_name" to storeName,
                    "store_type" to storeType,
                ),
        )
    }
}
