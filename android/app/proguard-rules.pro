# ProGuard rules for JaBook Flutter application
# Optimized for code shrinking, obfuscation, and APK size reduction

# ============================================================================
# Flutter Framework
# ============================================================================
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.embedding.** { *; }

# Flutter MethodChannel and Platform channels
-keep class io.flutter.plugin.common.** { *; }
-keep class io.flutter.plugin.platform.** { *; }

# ============================================================================
# Google Play Core (for deferred components - optional dependency)
# ============================================================================
# These classes are referenced by Flutter but may not be included in the APK
-dontwarn com.google.android.play.core.splitcompat.**
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**
-keep class com.google.android.play.core.splitcompat.** { *; }
-keep class com.google.android.play.core.splitinstall.** { *; }
-keep class com.google.android.play.core.tasks.** { *; }

# ============================================================================
# Kotlin Language
# ============================================================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin serialization (if used)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================================================
# Dagger Hilt - Dependency Injection
# ============================================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ============================================================================
# Media3 ExoPlayer - Native Audio Player
# ============================================================================
-keep class androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn androidx.media3.**

# ============================================================================
# Network Libraries
# ============================================================================
# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Dio (Flutter HTTP client)
-keep class dio.** { *; }
-dontwarn dio.**

# Retrofit (if used)
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ============================================================================
# Flutter Plugins - WebView
# ============================================================================
# webview_flutter
-keep class io.flutter.plugins.webviewflutter.** { *; }
-keep class android.webkit.** { *; }

# flutter_inappwebview
-keep class com.pichillilorenzo.flutter_inappwebview.** { *; }
-keep class android.webkit.** { *; }

# ============================================================================
# Flutter Plugins - Storage & Security
# ============================================================================
# flutter_secure_storage
-keep class com.it_nomads.fluttersecurestorage.** { *; }
-keep class androidx.security.crypto.** { *; }

# shared_preferences
-keep class io.flutter.plugins.sharedpreferences.** { *; }

# path_provider
-keep class io.flutter.plugins.pathprovider.** { *; }

# sembast (NoSQL database)
-keep class sembast.** { *; }
-keep class dart.io.** { *; }

# ============================================================================
# Flutter Plugins - Authentication & Permissions
# ============================================================================
# local_auth
-keep class io.flutter.plugins.localauth.** { *; }

# permission_handler
-keep class com.baseflow.permissionhandler.** { *; }

# ============================================================================
# Flutter Plugins - File & Media
# ============================================================================
# file_picker
-keep class com.mr.flutter.plugin.filepicker.** { *; }

# image_picker
-keep class io.flutter.plugins.imagepicker.** { *; }

# ============================================================================
# Flutter Plugins - Notifications & Background
# ============================================================================
# flutter_local_notifications
-keep class com.dexterous.flutterlocalnotifications.** { *; }
-keep class androidx.work.** { *; }

# workmanager
-keep class be.tramckrijte.workmanager.** { *; }
-keep class androidx.work.** { *; }

# ============================================================================
# Flutter Plugins - Other
# ============================================================================
# url_launcher
-keep class io.flutter.plugins.urllauncher.** { *; }

# share_plus
-keep class dev.fluttercommunity.plus.share.** { *; }

# device_info_plus
-keep class dev.fluttercommunity.plus.deviceinfo.** { *; }

# package_info_plus
-keep class dev.fluttercommunity.plus.packageinfo.** { *; }

# connectivity_plus
-keep class dev.fluttercommunity.plus.connectivity.** { *; }

# android_intent_plus
-keep class dev.fluttercommunity.plus.androidintent.** { *; }

# ============================================================================
# Custom Application Classes
# ============================================================================
# Keep custom Kotlin classes used by Flutter
-keep class com.jabook.app.jabook.** { *; }
-keep class com.jabook.app.jabook.audio.** { *; }
-keep class com.jabook.app.jabook.download.** { *; }

# Keep MainActivity and Application class
-keep class com.jabook.app.jabook.MainActivity { *; }
-keep class com.jabook.app.jabook.JabookApplication { *; }

# ============================================================================
# JSON Serialization (json_serializable, freezed)
# ============================================================================
# Keep classes with @JsonSerializable annotation
-keep @com.google.gson.annotations.SerializedName class * { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep freezed generated classes
-keep class **$* { *; }
-keep class **$*$* { *; }

# Keep classes with @freezed annotation
-keep @freezed.union.Union class * { *; }
-keepclassmembers class * {
    @freezed.** *;
}

# Keep JSON serialization methods
-keepclassmembers class * {
    <fields>;
}

# ============================================================================
# Riverpod & State Management
# ============================================================================
-keep class riverpod.** { *; }
-keep class flutter_riverpod.** { *; }

# ============================================================================
# GoRouter
# ============================================================================
-keep class go_router.** { *; }

# ============================================================================
# Torrent Libraries (if any native components)
# ============================================================================
-keep class com.frostwire.jlibtorrent.** { *; }
-keep class org.libtorrent4j.** { *; }
-dontwarn com.frostwire.jlibtorrent.**
-dontwarn org.libtorrent4j.**

# ============================================================================
# AndroidX Libraries
# ============================================================================
# Keep AndroidX classes that might be used via reflection
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep Android Support Library classes
-keep class android.support.** { *; }
-dontwarn android.support.**

# ============================================================================
# Android Platform Classes
# ============================================================================
# Keep Android classes used by plugins
-keep class android.app.** { *; }
-keep class android.content.** { *; }
-keep class android.os.** { *; }
-keep class android.net.** { *; }
-keep class android.webkit.** { *; }
-keep class android.media.** { *; }
-keep class android.provider.** { *; }

# ============================================================================
# Optimization Settings
# ============================================================================
# Aggressive optimization for smaller APK size
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove logging in release builds (reduces APK size)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove debug logging from Kotlin
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void println(...);
}

# ============================================================================
# Keep Native Methods
# ============================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# Keep Enums
# ============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# Keep Parcelable
# ============================================================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================================================
# Keep Serializable
# ============================================================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================================
# Keep R classes (but allow obfuscation of resource names)
# ============================================================================
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ============================================================================
# Keep View constructors for XML layouts
# ============================================================================
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============================================================================
# WebView JavaScript Interface
# ============================================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============================================================================
# Keep annotations (needed for reflection-based libraries)
# ============================================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================================================
# Suppress warnings for optional dependencies
# ============================================================================
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ============================================================================
# Apache Tika / javax.xml.stream (optional dependencies)
# ============================================================================
# These classes are referenced by some libraries but not available on Android
-dontwarn javax.xml.stream.**
-dontwarn org.apache.tika.**
