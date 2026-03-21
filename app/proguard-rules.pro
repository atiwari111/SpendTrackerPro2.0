# ════════════════════════════════════════════════════════════════
# SpendTracker Pro — ProGuard rules
#
# IMPORTANT: The old "-keep class com.spendtracker.pro.** { *; }"
# wildcard has been REMOVED. It prevented all obfuscation of our
# own code, making the release APK trivially reversible.
#
# Rules below are surgical: only what reflection actually needs is
# kept by name. Everything else (business logic, SMS parsers, DB
# queries, category engine) is fully obfuscated in release builds.
# ════════════════════════════════════════════════════════════════

# ── Keep all Activity/Service/Receiver/Provider entry points ──
# Android framework instantiates these by name via the manifest.
-keep public class * extends android.app.Activity
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.work.Worker

# ── Room — Entity field names must survive for SQLite mapping ──
# Keep field names on @Entity classes (Room maps column names to fields).
# Method bodies and internal helpers are still obfuscated.
-keepclassmembers @androidx.room.Entity class * {
    public <fields>;
}
# Keep DAO interfaces so Room's generated _Impl can reference them.
-keep @androidx.room.Dao interface * { *; }
# Keep the generated RoomDatabase subclass used at runtime.
-keep class * extends androidx.room.RoomDatabase { *; }

# ── Keep annotation metadata needed by Room's annotation processor
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

# ── MPAndroidChart ────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── Lottie ────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── DotsIndicator ─────────────────────────────────────────────
-keep class com.tbuonomo.viewpagerdotsindicator.** { *; }

# ── WorkManager ───────────────────────────────────────────────
# Worker subclasses are instantiated by class name at runtime.
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Biometric ─────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── Suppress warnings for unused optional dependencies ────────
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

# Keep TFLite classes
-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }

# Keep ML Agent classes
-keep class com.spendtracker.pro.TransactionFeatureExtractor { *; }
-keep class com.spendtracker.pro.TFLiteTransactionClassifier { *; }
