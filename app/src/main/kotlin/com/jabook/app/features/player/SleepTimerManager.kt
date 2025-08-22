package com.jabook.app.features.player

import android.os.Handler
import android.os.Looper
import com.jabook.app.shared.debug.IDebugLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager
    @Inject
    constructor(
        private val debugLogger: IDebugLogger,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var sleepTimerRunnable: Runnable? = null
        private var sleepTimerEndTime: Long = 0
        private var onTimerExpired: (() -> Unit)? = null

        fun setSleepTimer(
            minutes: Int,
            onExpired: () -> Unit,
        ) {
            debugLogger.logDebug("SleepTimerManager.setSleepTimer: $minutes minutes")

            cancelSleepTimer()

            sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000)
            onTimerExpired = onExpired

            sleepTimerRunnable =
                object : Runnable {
                    override fun run() {
                        val remainingTime = sleepTimerEndTime - System.currentTimeMillis()
                        if (remainingTime <= 0) {
                            // Timer expired
                            debugLogger.logInfo("SleepTimerManager.sleepTimer expired")
                            sleepTimerEndTime = 0
                            onTimerExpired?.invoke()
                        } else {
                            // Schedule next check
                            handler.postDelayed(this, 1000)
                        }
                    }
                }

            handler.post(sleepTimerRunnable!!)
        }

        fun cancelSleepTimer() {
            debugLogger.logDebug("SleepTimerManager.cancelSleepTimer called")
            sleepTimerRunnable?.let { handler.removeCallbacks(it) }
            sleepTimerRunnable = null
            sleepTimerEndTime = 0
            onTimerExpired = null
        }

        fun getSleepTimerRemaining(): Long =
            if (sleepTimerEndTime > 0) {
                maxOf(0, sleepTimerEndTime - System.currentTimeMillis())
            } else {
                0
            }

        fun isSleepTimerActive(): Boolean = sleepTimerEndTime > 0
    }
