# Keep OpenStreetMap/osmdroid classes that may be accessed indirectly at runtime.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# WebRTC is called from native code
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# WebRTC is reached from native code
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
