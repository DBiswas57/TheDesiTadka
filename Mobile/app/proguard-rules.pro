# StreamHub R8 / ProGuard Configuration

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.Room { *; }

# Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# BouncyCastle
-keep class org.bouncycastle.crypto.signers.Ed25519Signer { *; }
-keep class org.bouncycastle.crypto.params.Ed25519** { *; }
-dontwarn org.bouncycastle.**

# OkHttp & Jsoup
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# WorkManager Workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.thedesitadka.app.download.DownloadWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Strip debug logging in release builds
-assumenosideeffects class com.thedesitadka.core.security.StreamHubLogger {
    public static void d(java.lang.String, java.lang.String);
}
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

