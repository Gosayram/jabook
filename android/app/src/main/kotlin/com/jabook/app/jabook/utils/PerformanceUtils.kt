package com.jabook.app.jabook.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Classifies device performance to optimize app behavior.
 * Used to disable animations and complex effects on low-end devices.
 */
enum class PerformanceClass {
    LOW, // Android Go, < 3GB RAM, weak CPU
    MEDIUM, // Standard devices
    HIGH, // Flagships
}

object PerformanceUtils {
    @Volatile
    private var cachedPerformanceClass: PerformanceClass? = null

    fun getPerformanceClass(context: Context): PerformanceClass {
        cachedPerformanceClass?.let { return it }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Check for Low RAM device (Android Go explicitly)
        // isLowRamDevice is true for Android Go devices
        if (activityManager.isLowRamDevice) {
            cachedPerformanceClass = PerformanceClass.LOW
            return PerformanceClass.LOW
        }

        // Check RAM
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)

        // Devices with less than 3GB RAM are considered LOW end in 2025
        if (totalRamGb < 3.0) {
            cachedPerformanceClass = PerformanceClass.LOW
            return PerformanceClass.LOW
        }

        // Check CPU cores
        val cores = Runtime.getRuntime().availableProcessors()
        // Dual core or single core are definitely LOW end
        if (cores < 4) {
            cachedPerformanceClass = PerformanceClass.LOW
            return PerformanceClass.LOW
        }

        // High performance check:
        // - At least 6GB RAM
        // - At least 8 cores
        // - Android 12+ (SDK 31+)
        if (totalRamGb >= 6.0 && cores >= 8 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cachedPerformanceClass = PerformanceClass.HIGH
            return PerformanceClass.HIGH
        }

        // Everything else is MEDIUM
        cachedPerformanceClass = PerformanceClass.MEDIUM
        return PerformanceClass.MEDIUM
    }

    fun isLowEndDevice(context: Context): Boolean = getPerformanceClass(context) == PerformanceClass.LOW

    fun isHighEndDevice(context: Context): Boolean = getPerformanceClass(context) == PerformanceClass.HIGH
}
