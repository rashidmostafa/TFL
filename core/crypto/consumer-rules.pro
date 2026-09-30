# Applied to the app's R8 run. JNA binds native functions by reflection (method names and the
# classes of their parameters), so its classes and Lazysodium's native bindings must keep their names.

-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**

-keep class com.goterl.lazysodium.** { *; }
-keepclasseswithmembernames class com.goterl.lazysodium.** { native <methods>; }
