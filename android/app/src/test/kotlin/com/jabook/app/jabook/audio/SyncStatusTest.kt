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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusTest {
    // --- requiresAttention ---

    @Test
    fun `Synced does not require attention`() {
        assertFalse(SyncStatus.Synced.requiresAttention())
    }

    @Test
    fun `Syncing does not require attention`() {
        assertFalse(SyncStatus.Syncing.requiresAttention())
    }

    @Test
    fun `Pending with low count does not require attention`() {
        assertFalse(SyncStatus.Pending(5).requiresAttention())
    }

    @Test
    fun `Pending with high count requires attention`() {
        assertTrue(SyncStatus.Pending(15).requiresAttention())
    }

    @Test
    fun `Error requires attention`() {
        assertTrue(SyncStatus.Error("network timeout").requiresAttention())
    }

    // --- toLabel ---

    @Test
    fun `Synced label`() {
        assertEquals("Синхронизировано", SyncStatus.Synced.toLabel())
    }

    @Test
    fun `Syncing label`() {
        assertEquals("Синхронизация…", SyncStatus.Syncing.toLabel())
    }

    @Test
    fun `Pending label`() {
        assertEquals("Ожидает: 3", SyncStatus.Pending(3).toLabel())
    }

    @Test
    fun `Error label`() {
        assertEquals("Ошибка: timeout", SyncStatus.Error("timeout").toLabel())
    }
}
