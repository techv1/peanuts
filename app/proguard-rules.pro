# R8 Optimization & Code Shrinking Rules for peanuts

# Keep model data classes for Moshi JSON serialization
-keepclassmembers class com.studiodragon.peanuts.data.model.** { *; }
-keep class com.studiodragon.peanuts.data.model.** { *; }
-keepclassmembers class com.studiodragon.peanuts.data.OpenTopo** { *; }
-keep class com.studiodragon.peanuts.data.OpenTopo** { *; }

# Moshi Keep Rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }

# OkHttp Keep Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Javascript Interface for MapPicker WebView
-keepclassmembers class com.studiodragon.peanuts.ui.mappicker.MapBridge {
    @android.webkit.JavascriptInterface <methods>;
}
