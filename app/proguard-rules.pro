# Android ProGuard rules
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.squareup.okhttp.**

# Keep R (Resources) classes
-keep class ** extends android.app.Activity
-keep class ** extends android.app.Application
-keep class ** extends android.app.Service
-keep class ** extends android.view.View
-keep class ** extends android.content.Context

# Keep Kotlin
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keep class org.jetbrains.annotations.** { *; }

# Keep AndroidX
-dontwarn androidx.**
-keep class androidx.** { *; }

# Keep all activities
-keep class * extends android.app.Activity

# Keep all views
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep all Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}