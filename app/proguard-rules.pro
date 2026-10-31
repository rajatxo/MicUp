# Default ProGuard rules for MicUp.
# WebRTC / libwebrtc
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.getstream.webrtc.**

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
