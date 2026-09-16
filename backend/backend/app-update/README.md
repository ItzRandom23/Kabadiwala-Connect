# Android update channel

The Android app checks `/app/update.json` when it opens. To publish a test update, upload both files into this directory on the backend server:

```text
app-update/
  update.json
  kabadiwala-connect-beta-2.apk
```

Example `update.json`:

```json
{
  "versionCode": 3,
  "versionName": "1.0.0-beta.2",
  "apkUrl": "kabadiwala-connect-beta-2.apk",
  "releaseNotes": "Improved OTP recovery and stability."
}
```

Build the APK with a higher Android `versionCode` and sign it with the same signing key as the installed app. The app asks the user before downloading and Android asks for final installation confirmation; it never installs silently.
