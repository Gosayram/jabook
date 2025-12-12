# ProGuard rules for JaBook Kotlin/Android Application
# Optimized for R8, code shrinking, obfuscation, and APK size reduction
# Removed Flutter-specific rules, added Compose + modern AndroidX rules

# ============================================================================
# Kotlin Language
# ============================================================================
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jabook.app.jabook.**$$serializer { *; }
-keepclassmembers class com.jabook.app.jabook.** {
    *** Companion;
}
-keepclasseswithmembers class com.jabook.app.jabook.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# Jetpack Compose
# ============================================================================
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }

# Compose Navigation
-keep class androidx.navigation.compose.** { *; }

# Compose compiler generates classes we need to keep
-keep class **.*$Companion { *; }
-keepclassmembers class **.*$Companion { *; }

# ============================================================================
# Hilt - Dependency Injection
# ============================================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Keep Hilt generated components
-keep class **_HiltComponents { *; }
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# ============================================================================
# Room Database
# ============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Room-generated Kotlin code
-keep class **.*_Impl { *; }
-keep class **.*$Creator { *; }

# ============================================================================
# DataStore (Preferences + Proto)
# ============================================================================
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Proto DataStore generated classes
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences { *; }
-keep class **.*$Builder { *; }

# ============================================================================
# Google Tink - Encryption
# ============================================================================
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ============================================================================
# Media3 ExoPlayer - Native Audio Player
# ============================================================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Media3 Extractors (needed for audio formats)
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.decoder.** { *; }

# ============================================================================
# WorkManager
# ============================================================================
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ============================================================================
# Network Libraries
# ============================================================================
# OkHttp3
-keep,allowobfuscation,allowshrinking class okhttp3.**
-keep,allowobfuscation,allowshrinking interface okhttp3.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Retrofit2
-keep,allowobfuscation,allowshrinking class retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.**
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson (for JSON)
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes for Gson
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================================================
# Torrent Libraries
# ============================================================================
# jlibtorrent
-keep class com.frostwire.jlibtorrent.** { *; }
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# AndroidX Libraries
# ============================================================================
# Core AndroidX
-keep class androidx.core.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.lifecycle.** { *; }

# Android Support for backward compatibility
-dontwarn android.support.**

# ============================================================================
# Custom Application Classes
# ============================================================================
# Keep all app classes (adjust if using obfuscation)
-keep class com.jabook.app.jabook.** { *; }

# Keep MainActivity and Application
-keep class com.jabook.app.jabook.MainActivity { *; }
-keep class com.jabook.app.jabook.JabookApplication { *; }
-keep class com.jabook.app.jabook.compose.ComposeMainActivity { *; }

# Keep data models (for JSON/serialization)
-keep class com.jabook.app.jabook.compose.domain.model.** { *; }
-keep class com.jabook.app.jabook.compose.data.local.entity.** { *; }

# ============================================================================
# R8 Optimization Settings
# ============================================================================
# Enable aggressive optimization
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Remove Kotlin logging
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static *** println(...);
}

# ============================================================================
# Keep Standard Android Classes
# ============================================================================
# Native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep View constructors for XML layouts
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============================================================================
# Keep Attributes
# ============================================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile
-keepattributes LineNumberTable

# ============================================================================
# Suppress Warnings
# ============================================================================
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.tika.**
-dontwarn edu.umd.cs.findbugs.annotations.**
