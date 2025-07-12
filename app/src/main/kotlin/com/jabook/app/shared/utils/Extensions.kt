package com.jabook.app.shared.utils

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * String extensions
 */
fun String.isValidUrl(): Boolean {
    return this.startsWith("http://") || this.startsWith("https://")
}

fun String.isValidMagnetLink(): Boolean {
    return this.startsWith("magnet:?xt=urn:btih:")
}

fun String.sanitizeFileName(): String {
    return this.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

fun String.truncate(maxLength: Int): String {
    return if (this.length <= maxLength) this else "${this.substring(0, maxLength)}..."
}

/**
 * Long extensions for time and size formatting
 */
fun Long.formatAsTime(): String {
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    
    return when {
        hours > 0 -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun Long.formatAsSize(): String {
    return when {
        this >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", this / (1024.0 * 1024.0 * 1024.0))
        this >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", this / (1024.0 * 1024.0))
        this >= 1024 -> String.format(Locale.US, "%.1f KB", this / 1024.0)
        else -> String.format(Locale.US, "%d B", this)
    }
}

fun Long.formatAsDate(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(this))
}

fun Long.formatAsDateShort(): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(this))
}

/**
 * Float extensions
 */
fun Float.formatAsSpeed(): String {
    return String.format(Locale.US, "%.1fx", this)
}

fun Float.formatAsPercentage(): String {
    return String.format(Locale.US, "%.1f%%", this * 100)
}

/**
 * Context extensions
 */
fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        networkInfo?.isConnected == true
    }
}

fun Context.isWifiConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
    }
}

fun Context.shareText(text: String, title: String = "Share") {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    startActivity(Intent.createChooser(intent, title))
}

fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    startActivity(intent)
}

/**
 * Collection extensions
 */
fun <T> List<T>.safeGet(index: Int): T? {
    return if (index >= 0 && index < size) this[index] else null
}

fun <T> MutableList<T>.addIfNotExists(item: T) {
    if (!contains(item)) {
        add(item)
    }
}

fun <T> List<T>.chunkedSafe(size: Int): List<List<T>> {
    return if (size <= 0) listOf(this) else chunked(size)
}

/**
 * Validation utilities
 */
object ValidationUtils {
    
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    fun isValidUrl(url: String): Boolean {
        return android.util.Patterns.WEB_URL.matcher(url).matches()
    }
    
    fun isValidMagnetLink(magnetLink: String): Boolean {
        return magnetLink.matches(Regex("^magnet:\\?xt=urn:btih:[a-fA-F0-9]{40,}.*"))
    }
    
    fun isValidFileName(fileName: String): Boolean {
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return fileName.isNotBlank() && fileName.none { it in invalidChars }
    }
    
    fun isValidAudioFile(fileName: String): Boolean {
        val audioExtensions = listOf("mp3", "mp4", "m4a", "aac", "ogg", "flac", "wav")
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in audioExtensions
    }
}

/**
 * File utilities
 */
object FileUtils {
    
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }
    
    fun getFileNameWithoutExtension(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }
    
    fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
    
    fun createUniqueFileName(baseName: String, extension: String, existingFiles: List<String>): String {
        var counter = 1
        var fileName = "$baseName.$extension"
        
        while (existingFiles.contains(fileName)) {
            fileName = "${baseName}_$counter.$extension"
            counter++
        }
        
        return fileName
    }
}

/**
 * Text utilities
 */
object TextUtils {
    
    fun extractNumbers(text: String): List<Int> {
        return Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
    }
    
    fun removeHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
    }
    
    fun capitalizeWords(text: String): String {
        return text.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }
    
    fun truncateWithEllipsis(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text else "${text.substring(0, maxLength - 3)}..."
    }
}

/**
 * Math utilities
 */
object MathUtils {
    
    fun clamp(value: Int, min: Int, max: Int): Int {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    fun clamp(value: Long, min: Long, max: Long): Long {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
    
    fun map(value: Float, fromMin: Float, fromMax: Float, toMin: Float, toMax: Float): Float {
        val normalizedValue = (value - fromMin) / (fromMax - fromMin)
        return toMin + normalizedValue * (toMax - toMin)
    }
} 