-keep class com.spendtracker.pro.** { *; }
-keep class androidx.room.** { *; }

# ── MPAndroidChart ────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── Lottie ────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── DotsIndicator ─────────────────────────────────────────────
-keep class com.tbuonomo.viewpagerdotsindicator.** { *; }

# ── OkHttp ────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Room — keep generated _Impl classes ──────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Gson / JSON ───────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ── Biometric ─────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── WorkManager ───────────────────────────────────────────────
-keep class androidx.work.** { *; }

# ── Suppress common warnings ──────────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
