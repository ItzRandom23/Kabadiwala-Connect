# Kabadiwala Connect release rules. Minification is currently disabled while
# the release signing pipeline is being finalized; keep these boundaries
# explicit so enabling R8 later cannot remove reflective transport models.
-keep class com.irinteractivestudios.kabadiwalaconnect.data.remote.** { *; }
-keep class com.irinteractivestudios.kabadiwalaconnect.data.local.** { *; }
