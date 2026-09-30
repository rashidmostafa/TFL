# Protobuf lite (pairing codes): generated messages find their fields by name at run time, so R8
# must keep those names. Shipped inside :core:model's jar, where R8 picks it up automatically.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
