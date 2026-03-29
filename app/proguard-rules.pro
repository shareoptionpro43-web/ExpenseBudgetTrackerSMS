# Expense Tracker ProGuard Rules

# Keep application class
-keep class com.home.expensetracker.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Navigation Component
-keep class androidx.navigation.** { *; }

# ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Data binding / view binding
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# Serialization - keep data classes intact
-keepclassmembers class com.home.expensetracker.data.models.** {
    public <init>(...);
    public <fields>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
