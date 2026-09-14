# Kabadiwala Connect release rules. Preserve reflective transport and Room
# models while allowing R8 to optimize the rest of the application.
-keep class com.irinteractivestudios.kabadiwalaconnect.data.remote.** { *; }
-keep class com.irinteractivestudios.kabadiwalaconnect.data.local.** { *; }

# Tink references this compile-time-only Error Prone marker. It has no runtime
# behavior and is intentionally absent from the packaged application.
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn javax.lang.model.element.Modifier
