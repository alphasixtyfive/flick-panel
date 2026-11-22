# WebRTC uses JNI to find classes and callbacks by name.
-keep class org.webrtc.** { *; }
-keepclasseswithmembers class org.jni_zero.** {
    @org.jni_zero.CalledByNative <methods>;
}
