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

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlayerNotificationProviderTest {
    @Test
    fun `uses the service notification ID for Media3 provider callbacks`() {
        assertEquals(
            NotificationHelper.NOTIFICATION_ID,
            AudioPlayerNotificationProvider.MEDIA_NOTIFICATION_ID,
        )
    }

    @Test
    fun `maps Media3 player command buttons to saved notification slots`() {
        assertEquals(
            AudioPlayerNotificationProvider.SLOT_REWIND_30,
            AudioPlayerNotificationProvider.slotIdForPlayerCommand(Player.COMMAND_SEEK_BACK),
        )
        assertEquals(
            AudioPlayerNotificationProvider.SLOT_FORWARD_30,
            AudioPlayerNotificationProvider.slotIdForPlayerCommand(Player.COMMAND_SEEK_FORWARD),
        )
        assertEquals(
            AudioPlayerNotificationProvider.SLOT_CHAPTER_PREV,
            AudioPlayerNotificationProvider.slotIdForPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
        )
        assertEquals(
            AudioPlayerNotificationProvider.SLOT_CHAPTER_NEXT,
            AudioPlayerNotificationProvider.slotIdForPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        )
    }

    @Test
    fun `maps lockscreen privacy preference to notification visibility`() {
        assertEquals(
            android.app.Notification.VISIBILITY_PUBLIC,
            AudioPlayerNotificationProvider.visibilityFor(false),
        )
        assertEquals(
            android.app.Notification.VISIBILITY_PRIVATE,
            AudioPlayerNotificationProvider.visibilityFor(true),
        )
    }
}
