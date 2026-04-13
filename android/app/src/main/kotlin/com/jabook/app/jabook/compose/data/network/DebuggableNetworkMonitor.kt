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

package com.jabook.app.jabook.compose.data.network

import com.jabook.app.jabook.compose.data.debug.DebugRuntimeOverrides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network monitor that allows runtime debug override without changing production network plumbing.
 */
@Singleton
public class DebuggableNetworkMonitor
    @Inject
    constructor(
        private val actualNetworkMonitor: ConnectivityManagerNetworkMonitor,
        private val debugRuntimeOverrides: DebugRuntimeOverrides,
    ) : NetworkMonitor {
        override val networkType: Flow<NetworkType> =
            combine(
                actualNetworkMonitor.networkType,
                debugRuntimeOverrides.networkOverrideMode,
            ) { actual, _ ->
                debugRuntimeOverrides.resolveNetworkType(actual)
            }.distinctUntilChanged()

        override val isOnline: Flow<Boolean> =
            networkType
                .map { it != NetworkType.NONE }
                .distinctUntilChanged()
    }
