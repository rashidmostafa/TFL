# TFL release rules. Library keep rules (Hilt, Compose, Navigation) ship as consumer rules.

# Defense in depth for "logging is a no-op in release": TflLog's release sink already does nothing,
# and this strips any android.util.Log call left anywhere in the APK, including in libraries.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
