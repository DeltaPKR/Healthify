# ──────────────────────────────────────────────────────────────────────────
# Healthify ProGuard / R8 rules
#
# Most AndroidX / Compose / Kotlin libraries ship consumer ProGuard rules,
# so the project-level file stays small. Keep this list focused on:
#   - Reflection-sensitive code (Firebase, Room entities used as DTOs)
#   - Crash-report-friendliness (line numbers, source files)
#   - Things R8 cannot statically prove safe
# ──────────────────────────────────────────────────────────────────────────

# Keep source-file/line attributes so Crashlytics + Play Console stack traces
# point at real lines after minification.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin metadata so reflection-based libs (Firebase, Coroutines debug)
# still resolve class signatures.
-keepattributes RuntimeVisible*Annotations
-keepattributes InnerClasses,Signature,Exceptions,EnclosingMethod

# ── Coroutines ────────────────────────────────────────────────────────────
# kotlinx-coroutines ships its own rules, but a couple of extras keep ServiceLoader
# happy for the Main dispatcher resolution.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Room ──────────────────────────────────────────────────────────────────
# Room generates implementations at compile time via KSP. Entities are
# accessed by the generated DAO impls, but keeping them defensively avoids
# surprises if any code path uses reflection (e.g. type converters).
-keep class com.healthify.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Firebase ──────────────────────────────────────────────────────────────
# Firestore + Auth ship consumer rules; we only add safety for our own
# data classes that travel through `mapOf` writes — keeping their fields
# prevents R8 from renaming property names used as document field names.
-keepclassmembers class com.healthify.app.data.db.UserEntity { *; }
-keepclassmembers class com.healthify.app.data.db.CheckInEntity { *; }
-keepclassmembers class com.healthify.app.data.db.ReminderEntity { *; }

# Firebase analytics / measurement reflective lookups
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-dontwarn com.google.firebase.**

# ── Health Connect ────────────────────────────────────────────────────────
# androidx.health.connect provides its own consumer rules, but the client SDK
# resolves record classes by name in some paths.
-keep class androidx.health.connect.client.records.** { *; }
-dontwarn androidx.health.connect.**

# ── WorkManager ───────────────────────────────────────────────────────────
# Although the app no longer uses WorkManager for reminders, the dependency
# is still on the classpath. Keep any ListenableWorker subclass safe.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Compose / Lifecycle ──────────────────────────────────────────────────
# Compose compiler ships rules, but ViewModel reflection-instantiation is
# triggered by androidx.lifecycle.viewmodel.ViewModelProvider.
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}

# ── App glue ──────────────────────────────────────────────────────────────
# Application + Receiver classes referenced from the Manifest — R8 normally
# infers these, but keep them explicit to be safe across AGP versions.
-keep class com.healthify.app.HealthifyApp { *; }
-keep class com.healthify.app.MainActivity { *; }
-keep class com.healthify.app.notifications.ReminderReceiver { *; }
-keep class com.healthify.app.notifications.BootReceiver { *; }
