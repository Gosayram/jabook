# JaBook R8 rules (minimal - optimized for size)

# -------- Kotlin Metadata (essential only) --------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class **$WhenMappings { <fields>; }

# -------- Kotlinx Serialization (with @SerialName) --------
# Keep @Serializable classes
-keep,allowobfuscation @kotlinx.serialization.Serializable class **

# Keep generated serializers
-keep class **$$serializer { *; }

# CRITICAL: Keep @SerialName annotation values
-keep @interface kotlinx.serialization.SerialName
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep serializer companion method
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all serialization annotations
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# -------- Native Methods (JNI - cannot be obfuscated) --------
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# -------- Entry Points (Activities) --------
-keep class com.jabook.app.jabook.MainActivity
-keep class com.jabook.app.jabook.compose.ComposeMainActivity

# -------- DataStore Proto (app-specific) --------
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences { *; }
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences$* { *; }

# -------- Android Framework (required) --------
-keepclassmembers enum * { 
    public static **[] values(); 
    public static ** valueOf(java.lang.String); 
}

# -------- Remove Debug Logs --------
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
