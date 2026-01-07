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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for HeadsetAutoplayHandler.
 */
@RunWith(RobolectricTestRunner::class)
class HeadsetAutoplayHandlerTest {
    private lateinit var context: Context
    private lateinit var handler: HeadsetAutoplayHandler
    private var callbackInvoked = false

    @Before
    fun setup() {
        context = mock()
        callbackInvoked = false
        handler = HeadsetAutoplayHandler(context) { callbackInvoked = true }
    }

    @Test
    fun `startListening registers receiver`() {
        handler.startListening()
        verify(context).registerReceiver(any(), any())
    }

    @Test
    fun `stopListening unregisters receiver`() {
        handler.startListening()
        handler.stopListening()
        verify(context).unregisterReceiver(any())
    }

    @Test
    fun `receiver ignores duplicate startListening`() {
        handler.startListening()
        handler.startListening()
        verify(context, times(1)).registerReceiver(any(), any())
    }

    @Test
    fun `stopListening handles unregistered state safely`() {
        handler.stopListening()
        // Should not throw
    }
}
