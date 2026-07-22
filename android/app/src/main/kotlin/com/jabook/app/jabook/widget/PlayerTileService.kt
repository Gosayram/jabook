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

package com.jabook.app.jabook.widget

import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.util.LogUtils
import java.util.concurrent.TimeUnit

public class PlayerTileService : TileService() {
    override fun onStartListening() {
        updateTileFromPlayer()
    }

    override fun onStopListening() {
        // no-op, tile will be updated on next onStartListening
    }

    override fun onClick() {
        val intent =
            Intent(this, AudioPlayerService::class.java).apply {
                action = "com.jabook.app.jabook.WIDGET_PLAY_PAUSE"
            }
        startForegroundService(intent)
    }

    private fun updateTileFromPlayer() {
        val tile = qsTile ?: return

        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(R.string.no_book_playing)
        tile.contentDescription = getString(R.string.app_name)

        var controller: MediaController? = null
        var controllerFuture: ListenableFuture<MediaController>? = null

        try {
            val sessionToken =
                SessionToken(this, ComponentName(this, AudioPlayerService::class.java))
            controllerFuture =
                MediaController.Builder(this, sessionToken).buildAsync()

            controller =
                controllerFuture.get(TILE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)

            if (controller != null) {
                val isPlaying = controller.isPlaying
                val metadata = controller.currentMediaItem?.mediaMetadata

                tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label =
                    metadata?.albumTitle?.toString()
                        ?: metadata?.title?.toString()
                        ?: getString(R.string.app_name)
                tile.subtitle =
                    if (isPlaying) {
                        getString(R.string.quick_tile_playing)
                    } else {
                        getString(R.string.quick_tile_paused)
                    }
            }
        } catch (e: Exception) {
            LogUtils.w("PlayerTile", "Failed to get player state", e)
        } finally {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }

        tile.updateTile()
    }

    public companion object {
        private const val TILE_TIMEOUT_SECONDS: Int = 2
    }
}
