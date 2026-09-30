# Beta APK publishing

Current channel: **0.1.12-beta (76)**.

| Field | Value |
| --- | --- |
| APK | kabadiwala-connect-0.1.12-beta-debug.apk |
| Variant | envTestingDebug |
| Size | 31,835,140 bytes |
| SHA-256 | E0743A23AA688BC53308B5ABEF0A3F0D4B9E306A43E5644F85790DF015A13BB0 |

[update.json](update.json) is authoritative. The backend serves this directory under /app/, including /app/update.json. Relative apkUrl resolves within that path.

This is a debug-signed testing beta, not a production-signed release. Check its API endpoint before distribution.

## Publishing procedure

1. Increase versionName and monotonically increase versionCode in Android app/build.gradle.kts.
2. Build the intended variant against the intended backend.
3. Copy its APK here using a versioned filename.
4. Update manifest version, filename, checksum, size and release notes.
5. Publish APK and manifest together to the served update path.
6. Verify download, checksum and upgrade behavior.

From the repository root:

```powershell
$betaApk = Get-Item -LiteralPath 'backend/app-update/kabadiwala-connect-0.1.12-beta-debug.apk'
$betaApk.Length
(Get-FileHash -LiteralPath $betaApk.FullName -Algorithm SHA256).Hash
Get-Content -LiteralPath 'backend/app-update/update.json'
```

An upgrade needs the same application ID and signing key as the installed app. A higher version code does not fix a signing mismatch. Keep production signing credentials outside Git.

GitHub publication alone does not update a running backend. Deploy contracts before dependent clients. The **7:30 AM–9:30 PM** pickup window requires matching backend enforcement and Android presentation.

See [build instructions](../../README.md) and [verification status](../../docs/README.md). A successful APK build is not full runtime acceptance.
