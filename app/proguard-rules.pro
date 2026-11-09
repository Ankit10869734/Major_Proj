# MoodMix Proguard / R8 Keep Rules

# OkHttp rules
-keepattributes Signature
-keepattributes AnnotationDefault
-keepclassmembers class * extends okhttp3.internal.concurrent.TaskQueue$Task {
    *;
}

# Media3 / ExoPlayer rules
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# ML Kit rules
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Coil 3 rules
-dontwarn io.coilkt.**

# MoodMix Domain Models & DTOs
-keep class com.moodmix.app.domain.model.** { *; }
-keep class com.moodmix.app.network.** { *; }
-keep class com.moodmix.app.audio.** { *; }
-keep class com.moodmix.app.lyrics.** { *; }
