# Retrofit: keep HTTP method annotations and generic signatures
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-dontwarn retrofit2.**
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Retrofit suspend functions: keep Continuation generic signature
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Gson: keep fields used for serialization
-keep class com.raund.app.data.remote.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room entities
-keep class com.raund.app.data.entity.** { *; }
-keep class com.raund.app.data.dao.RoundStats { *; }

# Parcelize (TimerProfile, TimerRound, TimerState)
-keep class com.raund.app.timer.** { *; }

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# ViewModels and SaveResult (used by Compose/ViewModelProvider and type checks)
-keep class com.raund.app.viewmodel.** { *; }

# Sentry
-keepattributes LineNumberTable,SourceFile
-dontwarn io.sentry.**
-keep class io.sentry.** { *; }
