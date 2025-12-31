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

package com.jabook.app.jabook.compose.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles notification action button clicks for torrent downloads
 */
@AndroidEntryPoint
class TorrentActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var torrentManager: TorrentManager

    @Inject
    lateinit var notificationManager: TorrentNotificationManager

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val hash = intent.getStringExtra(EXTRA_TORRENT_HASH)

        when (intent.action) {
            ACTION_PAUSE_TORRENT -> {
                hash?.let {
                    torrentManager.pauseTorrent(it)
                    updateNotification(it)
                    Log.i(TAG, "Paused torrent: $it")
                }
            }

            ACTION_RESUME_TORRENT -> {
                hash?.let {
                    torrentManager.resumeTorrent(it)
                    updateNotification(it)
                    Log.i(TAG, "Resumed torrent: $it")
                }
            }

            ACTION_STOP_TORRENT -> {
                hash?.let {
                    torrentManager.stopTorrent(it, deleteFiles = false)
                    notificationManager.cancel(it.hashCode())
                    Log.i(TAG, "Stopped torrent: $it")
                }
            }

            ACTION_CANCEL_TORRENT -> {
                hash?.let {
                    torrentManager.removeTorrent(it, deleteFiles = true)
                    notificationManager.cancel(it.hashCode())
                    Log.i(TAG, "Cancelled torrent: $it")
                }
            }

            ACTION_PAUSE_ALL -> {
                torrentManager.pauseAll()
                notificationManager.updateAllNotifications()
                Log.i(TAG, "Paused all torrents")
            }

            ACTION_RESUME_ALL -> {
                torrentManager.resumeAll()
                notificationManager.updateAllNotifications()
                Log.i(TAG, "Resumed all torrents")
            }
        }
    }

    private fun updateNotification(hash: String) {
        torrentManager.getDownload(hash)?.let { download ->
            notificationManager.updateNotification(download)
        }
    }

    companion object {
        private const val TAG = "TorrentActionReceiver"

        const val ACTION_PAUSE_TORRENT = "org.jabook.ACTION_PAUSE_TORRENT"
        const val ACTION_RESUME_TORRENT = "org.jabook.ACTION_RESUME_TORRENT"
        const val ACTION_STOP_TORRENT = "org.jabook.ACTION_STOP_TORRENT"
        const val ACTION_CANCEL_TORRENT = "org.jabook.ACTION_CANCEL_TORRENT"
        const val ACTION_PAUSE_ALL = "org.jabook.ACTION_PAUSE_ALL"
        const val ACTION_RESUME_ALL = "org.jabook.ACTION_RESUME_ALL"

        const val EXTRA_TORRENT_HASH = "torrent_hash"
    }
}
