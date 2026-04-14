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

package com.jabook.app.jabook.compose.feature.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified Player flow contract.
 *
 * State: long-living observable source of truth.
 * Event: one-shot UI side effects.
 * Command: execution pipeline from intents to side-effect handlers.
 */
public typealias PlayerStateFlowContract = StateFlow<PlayerState>
public typealias PlayerEventFlowContract = SharedFlow<PlayerEffect>
internal typealias PlayerCommandFlowContract = Flow<PlayerCommand>
