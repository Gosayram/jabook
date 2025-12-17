# JaBook R8 rules (release)

# -------- Kotlin / metadata --------
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod

# -------- Kotlinx Serialization (optimized for @SerialName) --------
# Keep @Serializable classes and their serializers
-keep,allowobfuscation @kotlinx.serialization.Serializable class **

# Keep generated serializers
-keep,includedescriptorclasses class **$$serializer { *; }

# Keep serializer companion objects
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @SerialName and other serialization annotations
-keepattributes RuntimeVisibleAnnotations



# -------- Hilt (optimized) --------
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# -------- Room (optimized) --------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# -------- DataStore Proto (specific) --------
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences { *; }
-keep class com.jabook.app.jabook.compose.data.preferences.UserPreferences$* { *; }

# -------- WorkManager --------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends androidx.work.Worker {
  public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -------- Tink --------
-dontwarn com.google.crypto.tink.**

# -------- Media3 --------
-dontwarn androidx.media3.**

# -------- Retrofit / OkHttp (let consumer rules handle it) --------
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# -------- Gson REMOVED (app uses kotlinx.serialization) --------

# -------- Torrent / JNI --------
-keep class com.frostwire.jlibtorrent.** { *; }
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# -------- App entry points --------
-keep class com.jabook.app.jabook.MainActivity { *; }
-keep class com.jabook.app.jabook.compose.ComposeMainActivity { *; }
-keep class com.jabook.app.jabook.JabookApplication { *; }

# -------- Strip logs in release --------
-assumenosideeffects class android.util.Log {
  public static *** v(...);
  public static *** d(...);
  public static *** i(...);
  public static *** w(...);
  public static *** e(...);
}
-assumenosideeffects class kotlin.io.ConsoleKt { public static *** println(...); }

# -------- Android framework keep patterns --------
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keep class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }
-keepclassmembers class * implements java.io.Serializable {
  static final long serialVersionUID;
  private static final java.io.ObjectStreamField[] serialPersistentFields;
  private void writeObject(java.io.ObjectOutputStream);
  private void readObject(java.io.ObjectInputStream);
  java.lang.Object writeReplace();
  java.lang.Object readResolve();
}
-keepclasseswithmembers class * { public <init>(android.content.Context, android.util.AttributeSet); }
-keepclasseswithmembers class * { public <init>(android.content.Context, android.util.AttributeSet, int); }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# -------- Suppress noisy warnings --------
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.tika.**
-dontwarn edu.umd.cs.findbugs.annotations.**
