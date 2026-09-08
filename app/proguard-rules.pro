# ===== Hilt =====
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint { *; }

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.room.** { *; }

# ===== Coroutines =====
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# ===== Serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$Companion { *; }
-keep class kotlin.Metadata { *; }

# ===== Compose =====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===== App-specific =====
-keep class com.my.amali.data.model.** { *; }
-keep class com.my.amali.data.ai.** { *; }
-keep class com.my.amali.domain.entity.** { *; }
