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

package com.jabook.app.jabook.compose.data.backup

/**
 * Policy used when imported backup data conflicts with existing local data.
 */
public enum class ConflictResolutionPolicy {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_NEWER,
}

/**
 * Shared resolver for backup merge conflicts.
 */
public object ConflictResolutionResolver {
    /**
     * Returns true when incoming backup data should replace local data.
     */
    public fun shouldUseIncoming(
        policy: ConflictResolutionPolicy,
        localExists: Boolean,
        localTimestamp: Long,
        incomingTimestamp: Long,
    ): Boolean {
        if (!localExists) return true

        return when (policy) {
            ConflictResolutionPolicy.KEEP_LOCAL -> false
            ConflictResolutionPolicy.KEEP_REMOTE -> true
            ConflictResolutionPolicy.KEEP_NEWER -> incomingTimestamp >= localTimestamp
        }
    }
}
