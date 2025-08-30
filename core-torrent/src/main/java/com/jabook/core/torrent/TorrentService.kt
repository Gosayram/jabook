package com.jabook.core.torrent

import android.content.Context
import android.util.Log
import com.jabook.core.endpoints.EndpointResolver
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-stable, K2-friendly torrent service:
 * - no Kotlin Result<T>, no functional DSL, no reflection on SessionHandle
 * - keep our own map of active handles, no session.torrents()
 * - use only stable libtorrent4j calls
 */
class TorrentService(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER")
    private val endpointResolver: EndpointResolver
) {
    private val TAG = "TorrentService"

    private var sessionManager: SessionManager? = null
    private val initialized = AtomicBoolean(false)

    private val handles = ConcurrentHashMap<String, TorrentHandle>()
    private val paused = ConcurrentHashMap<String, Boolean>()
    private val finishedOnce = ConcurrentHashMap<String, Boolean>()
    private val listeners = ConcurrentHashMap<String, TorrentListener>()

    private var timer: Timer? = null

    interface TorrentListener {
        fun onProgress(torrentId: String, progress: Float, downloadSpeed: Long, uploadSpeed: Long)
        fun onFinished(torrentId: String)
        fun onError(torrentId: String, error: String)
        fun onPaused(torrentId: String)
    }

    data class TorrentSnapshot(
        val id: String,
        val name: String,
        val size: Long,
        val progress: Float,
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val priority: Int,
        val sequential: Boolean,
        val paused: Boolean,
        val finished: Boolean
    )

    init { initSession() }

    private fun initSession() {
        if (initialized.get()) return
        try {
            val sm = SessionManager()
            sm.start()

            val sp = SettingsPack()
            sp.downloadRateLimit(0)
            sp.uploadRateLimit(0)
            sp.activeDownloads(5)
            sp.activeSeeds(3)
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
            sm.applySettings(sp)

            sessionManager = sm
            initialized.set(true)
            Log.i(TAG, "Torrent service initialized")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize SessionManager", t)
            throw IllegalStateException("Torrent init failed", t)
        }
    }

    private fun ensureReady(): SessionManager {
        val sm = sessionManager
        if (!initialized.get() || sm == null) {
            throw IllegalStateException("Torrent service not initialized")
        }
        return sm
    }

    // ---------------- Public API ----------------

    /** Add torrent from magnet link; returns torrent id */
    fun addTorrentByMagnet(magnetUrl: String): String {
        val sm = ensureReady()
        try {
            val params = AddTorrentParams.parseMagnetUri(magnetUrl)
            params.savePath(torrentsDir().absolutePath)

            val handle = sm.addTorrent(params)
            val id = idHex(handle)

            handles[id] = handle
            paused[id] = false
            finishedOnce[id] = false

            Log.i(TAG, "Added magnet $id")
            return id
        } catch (t: Throwable) {
            Log.e(TAG, "addTorrentByMagnet failed", t)
            throw t
        }
    }

    /** Add torrent from .torrent file; returns torrent id */
    fun addTorrentByFile(torrentFile: File): String {
        val sm = ensureReady()
        try {
            val ti = TorrentInfo(torrentFile)
            val params = AddTorrentParams()
            params.torrentInfo(ti)
            params.savePath(torrentsDir().absolutePath)

            val handle = sm.addTorrent(params)
            val id = idHex(handle)

            handles[id] = handle
            paused[id] = false
            finishedOnce[id] = false

            Log.i(TAG, "Added file $id")
            return id
        } catch (t: Throwable) {
            Log.e(TAG, "addTorrentByFile failed", t)
            throw t
        }
    }

    /** Remove from our map and try to stop in libtorrent (best-effort). */
    fun removeTorrent(torrentId: String, deleteFiles: Boolean) {
        val h = handles.remove(torrentId) ?: return
        try { h.pause() } catch (_: Throwable) { }
        try { h.flushCache() } catch (_: Throwable) { }
        try { h.forceRecheck() } catch (_: Throwable) { } // harmless

        paused.remove(torrentId)
        finishedOnce.remove(torrentId)

        if (deleteFiles) {
            try { torrentFilesDir(torrentId).deleteRecursively() } catch (_: Throwable) { }
        }
        Log.i(TAG, "Removed $torrentId (best-effort)")
    }

    fun pauseTorrent(torrentId: String) {
        val h = handles[torrentId] ?: return
        try { h.pause() } catch (_: Throwable) { }
        paused[torrentId] = true
        notifyPaused(torrentId)
    }

    fun resumeTorrent(torrentId: String) {
        val h = handles[torrentId] ?: return
        try { h.resume() } catch (_: Throwable) { }
        paused[torrentId] = false
    }

    fun getTorrentInfo(torrentId: String): TorrentSnapshot? {
        val h = handles[torrentId] ?: return null
        return snapshot(h, torrentId)
    }

    fun getAllTorrents(): List<TorrentSnapshot> {
        val list = ArrayList<TorrentSnapshot>(handles.size)
        val it = handles.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val snap = snapshot(e.value, e.key)
            if (snap != null) list.add(snap)
        }
        return list
    }

    fun setTorrentListener(torrentId: String, listener: TorrentListener) {
        listeners[torrentId] = listener
    }

    fun removeTorrentListener(torrentId: String) {
        listeners.remove(torrentId)
    }

    // ---------------- Polling ----------------

    fun startProgressMonitoring() {
        stopProgressMonitoring()
        val t = Timer("torrent-progress", true)
        t.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try { pollOnce() } catch (t: Throwable) {
                    Log.e(TAG, "progress loop error", t)
                }
            }
        }, 1000L, 1000L)
        timer = t
    }

    fun stopProgressMonitoring() {
        try { timer?.cancel() } catch (_: Throwable) { }
        timer = null
    }

    private fun pollOnce() {
        val it = handles.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val id = e.key
            val h = e.value
            try {
                val st = h.status()
                val l = listeners[id]
                if (l != null) {
                    try { l.onProgress(id, st.progress(), st.downloadRate().toLong(), st.uploadRate().toLong()) }
                    catch (_: Throwable) { }
                }
                val fin = isFinished(st)
                val once = finishedOnce[id] == true
                if (fin && !once) {
                    finishedOnce[id] = true
                    if (l != null) {
                        try { l.onFinished(id) } catch (_: Throwable) { }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "pollOnce fail for $id", t)
            }
        }
    }

    // ---------------- Utils ----------------

    private fun snapshot(h: TorrentHandle, id: String): TorrentSnapshot? {
        return try {
            val st = h.status()
            TorrentSnapshot(
                id = id,
                name = safeName(h),
                size = st.totalWanted(),
                progress = st.progress(),
                downloadSpeed = st.downloadRate().toLong(),
                uploadSpeed = st.uploadRate().toLong(),
                priority = 1,
                sequential = false,
                paused = paused[id] == true,
                finished = isFinished(st)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "snapshot failed for $id", t)
            null
        }
    }

    private fun isFinished(st: TorrentStatus): Boolean {
        return try {
            val s = st.state()
            s == TorrentStatus.State.FINISHED || s == TorrentStatus.State.SEEDING
        } catch (_: Throwable) { false }
    }

    private fun notifyPaused(torrentId: String) {
        val l = listeners[torrentId]
        if (l != null) {
            try { l.onPaused(torrentId) } catch (_: Throwable) { }
        }
    }

    private fun idHex(handle: TorrentHandle): String {
        // Ensure this is ALWAYS String for K2
        return try {
            val s = handle.infoHash().toString()
            s ?: "unknown" // (s is already String, but defensively)
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun safeName(h: TorrentHandle): String {
        return try {
            val n = h.name()
            n ?: "unknown"
        } catch (_: Throwable) { "unknown" }
    }

    private fun torrentsDir(): File {
        val dir = File(context.filesDir, "torrents")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun torrentFilesDir(torrentId: String): File {
        return File(torrentsDir(), torrentId)
    }

    fun isTorrentFinished(torrentId: String): Boolean {
        val s = getTorrentInfo(torrentId)
        return s?.finished == true
    }

    fun getTorrentFilePath(torrentId: String, fileName: String): String? {
        return try {
            val f = File(torrentFilesDir(torrentId), fileName)
            if (f.exists()) f.absolutePath else null
        } catch (t: Throwable) {
            Log.e(TAG, "getTorrentFilePath failed", t)
            null
        }
    }

    fun cleanup() {
        stopProgressMonitoring()
        try { sessionManager?.stop() } catch (_: Throwable) { }
        sessionManager = null
        initialized.set(false)
        handles.clear()
        paused.clear()
        finishedOnce.clear()
        listeners.clear()
        Log.i(TAG, "Torrent service cleaned up")
    }
}