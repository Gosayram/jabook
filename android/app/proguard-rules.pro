# JaBook R8 rules (minimal - optimized for size)

# TASK-VERM-22: R8 full mode optimization enabled.
# The previous -dontoptimize workaround for IndexOutOfBoundsException has been removed.
# Keep rules below are sufficient for R8 full mode compatibility.
# If R8 crashes recur, add -dontoptimize back and file a bug.
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# -------- Kotlin Metadata (essential only) --------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class **$WhenMappings { <fields>; }

# -------- Kotlinx Serialization (ENHANCED - R8 FULL MODE COMPATIBLE) --------

# Keep all kotlinx.serialization core classes
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }

# Keep all @Serializable classes WITHOUT obfuscation
-keep @kotlinx.serialization.Serializable class ** {
    *;
}

# Keep all fields marked with @SerialName
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep generated serializers ($$serializer classes)
-keep class **$$serializer {
    *;
}

# Keep serializer() methods in companion objects
-keepclassmembers class * {
    static ** Companion;
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep navigation routes specifically (belt-and-suspenders approach)
-keep class com.jabook.app.jabook.compose.navigation.** implements kotlinx.serialization.KSerializer { *; }
-keep class com.jabook.app.jabook.compose.navigation.**Route {
    *;
}
-keep class com.jabook.app.jabook.compose.navigation.**Route$$serializer {
    *;
}

# Prevent R8 from removing or inlining serialization descriptors
-keepclassmembers class kotlinx.serialization.internal.** {
    *;
}

# Keep all serialization annotations
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations

# -------- Hilt (Dependency Injection) --------
# Keep entry points and generated components
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class **_Factory { *; }
-keep class **_HiltComponents { *; }

# -------- Room (Database) --------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class **_Impl { *; }

# -------- DataStore Proto --------
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences { *; }
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences$* { *; }
# Keep protobuf-lite runtime/message members used by generated serializers and reflection bridges
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    *;
}

# -------- Retrofit / OkHttp --------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# -------- Native Methods --------
-keepclasseswithmembernames class * {
    native <methods>;
}

# -------- libtorrent4j (CRITICAL - prevent obfuscation) --------
# Keep all libtorrent4j classes to prevent NoSuchMethodError
-keep class org.libtorrent4j.** { *; }
-keep class org.libtorrent4j.swig.** { *; }
-keepclassmembers class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.swig.** { *; }
# Keep native methods in libtorrent4j
-keepclasseswithmembernames class org.libtorrent4j.swig.** {
    native <methods>;
}
# Keep static methods that may be called from native code
-keepclassmembers class org.libtorrent4j.swig.** {
    static <methods>;
}
# Don't warn about missing classes (native library handles this)
-dontwarn org.libtorrent4j.**
-dontwarn org.libtorrent4j.swig.**

# -------- KTagLib JNI bridge --------
# Keep ktaglib API/jni-facing symbols stable for native calls
-keep class com.simplecityapps.ktaglib.** { *; }
-keepclasseswithmembernames class com.simplecityapps.ktaglib.** {
    native <methods>;
}

# -------- Entry Points (Activities) --------
-keep class com.jabook.app.jabook.MainActivity
-keep class com.jabook.app.jabook.compose.ComposeMainActivity

# -------- Android Framework (required) --------
-keepclassmembers enum * { 
    public static **[] values(); 
    public static ** valueOf(java.lang.String); 
}

# -------- Logging --------
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# -------- Suppress Warnings (libraries with consumer-rules) --------
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.crypto.tink.**
-dontwarn androidx.media3.**
-dontwarn androidx.room.paging.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.tika.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# -------- Media3 (CRITICAL - prevents CrashLoop) --------
# Keep MediaSession and CommandButton classes to prevent obfuscation issues
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.Player$Listener { *; }
-keep interface androidx.media3.common.Player$Listener { *; }

# Keep CommandButton.Builder to prevent icon resource ID obfuscation
-keep class androidx.media3.session.CommandButton { *; }
-keep class androidx.media3.session.CommandButton$Builder { *; }
-keepclassmembers class androidx.media3.session.CommandButton$Builder {
    *;
}

# Keep MediaLibrarySession callback methods
-keep class * extends androidx.media3.session.MediaLibraryService$MediaLibrarySession$Callback {
    *;
}
