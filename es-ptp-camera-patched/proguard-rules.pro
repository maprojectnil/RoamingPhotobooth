# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep PTP classes
-keep class com.extremesolution.esptpcamera.** { *; }
-keepclassmembers class com.extremesolution.esptpcamera.** { *; }
