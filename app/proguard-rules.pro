# Default ProGuard rules for MicUp.
# Agora voice SDK
-keep class io.agora.** { *; }
-keep class io.agora.rtc.** { *; }
-keep class io.agora.rtc2.** { *; }
-dontwarn io.agora.**
-dontwarn io.agora.rtc.**
-dontwarn io.agora.rtc2.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rajatxo.micup.**$$serializer { *; }
-keepclassmembers class com.rajatxo.micup.** {
    *** Companion;
}
-keepclasseswithmembers class com.rajatxo.micup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
