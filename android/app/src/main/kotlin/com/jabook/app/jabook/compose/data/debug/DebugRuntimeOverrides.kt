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

package com.jabook.app.jabook.compose.data.debug

import com.jabook.app.jabook.compose.data.network.NetworkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime-only debug overrides shared by diagnostics surfaces.
 */
@Singleton
public class DebugRuntimeOverrides
    @Inject
    constructor() {
        private val _networkOverrideMode = MutableStateFlow(DebugNetworkOverrideMode.AUTO)
        public val networkOverrideMode: StateFlow<DebugNetworkOverrideMode> = _networkOverrideMode.asStateFlow()

        private val _forceLowStorage = MutableStateFlow(false)
        public val forceLowStorage: StateFlow<Boolean> = _forceLowStorage.asStateFlow()

        public fun setNetworkOverrideMode(mode: DebugNetworkOverrideMode) {
            _networkOverrideMode.value = mode
        }

        public fun setForceLowStorage(enabled: Boolean) {
            _forceLowStorage.value = enabled
        }

        public fun isForceLowStorageEnabled(): Boolean = _forceLowStorage.value

        public fun resolveNetworkType(actual: NetworkType): NetworkType =
            when (_networkOverrideMode.value) {
                DebugNetworkOverrideMode.AUTO -> actual
                DebugNetworkOverrideMode.FORCE_OFFLINE -> NetworkType.NONE
                DebugNetworkOverrideMode.FORCE_METERED -> NetworkType.CELLULAR
                DebugNetworkOverrideMode.FORCE_UNMETERED_WIFI -> NetworkType.WIFI
            }
    }

public enum class DebugNetworkOverrideMode {
    AUTO,
    FORCE_OFFLINE,
    FORCE_METERED,
    FORCE_UNMETERED_WIFI,
}
