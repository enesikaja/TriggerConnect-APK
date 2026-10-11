# ---- Kotlinx Serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.triggerconnect.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.triggerconnect.app.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.triggerconnect.app.** { *; }

# ---- Supabase / Ktor ----
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- Firebase Cloud Messaging ----
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---- ML Kit barcode ----
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- Models (reflection-free, but keep for safety on serialized DTOs) ----
-keep class com.triggerconnect.app.data.remote.dto.** { *; }

# ---- Keep Hilt generated ----
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
