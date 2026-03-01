# Retrofit: keep HTTP method annotations and generic signatures
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson: keep fields used for serialization
-keep class com.raund.app.data.remote.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Room entities
-keep class com.raund.app.data.entity.** { *; }
-keep class com.raund.app.data.dao.RoundStats { *; }

# Parcelize (TimerProfile, TimerRound, TimerState)
-keep class com.raund.app.timer.** { *; }

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
