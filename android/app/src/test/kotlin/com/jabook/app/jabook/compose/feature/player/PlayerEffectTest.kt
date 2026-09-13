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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerEffectTest {
    @Test
    fun `ShowSnackbar has correct properties`() {
        val intent = PlayerIntent.TogglePlayPause
        val effect =
            PlayerEffect.ShowSnackbar(
                message = "Test message",
                actionLabel = "Undo",
                actionIntent = intent,
            )

        assertThat(effect.message).isEqualTo("Test message")
        assertThat(effect.actionLabel).isEqualTo("Undo")
        assertThat(effect.actionIntent).isNotNull()
    }

    @Test
    fun `ShowSnackbar without action has null actionIntent`() {
        val effect = PlayerEffect.ShowSnackbar(message = "Simple message")

        assertThat(effect.message).isEqualTo("Simple message")
        assertThat(effect.actionLabel).isNull()
        assertThat(effect.actionIntent).isNull()
    }

    @Test
    fun `NavigateBack is singleton`() {
        val nav1 = PlayerEffect.NavigateBack
        val nav2 = PlayerEffect.NavigateBack

        assertThat(nav1).isSameInstanceAs(nav2)
    }

    @Test
    fun `NavigateToBook has correct bookId`() {
        val effect = PlayerEffect.NavigateToBook("book-123")

        assertThat(effect.bookId).isEqualTo("book-123")
    }

    @Test
    fun `ShowError is data class`() {
        val error1 = PlayerEffect.ShowError("Error 1")
        val error2 = PlayerEffect.ShowError("Error 1")

        assertThat(error1).isEqualTo(error2)
    }
}
