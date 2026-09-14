# Kabadiwala Connect release rules. Preserve reflective transport and Room
# models while allowing R8 to optimize the rest of the application.
-keep class com.irinteractivestudios.kabadiwalaconnect.data.remote.** { *; }
-keep class com.irinteractivestudios.kabadiwalaconnect.data.local.** { *; }

# WorkManager creates the default input merger and workers reflectively. Keep
# their public constructors in minified variants so queued offline operations
# can run after process death and relaunch.
-keep class androidx.work.OverwritingInputMerger { public <init>(); }
-keep class com.irinteractivestudios.kabadiwalaconnect.data.sync.SyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Tink references this compile-time-only Error Prone marker. It has no runtime
# behavior and is intentionally absent from the packaged application.
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn javax.lang.model.element.Modifier
