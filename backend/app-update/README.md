# Beta APK publishing

Current channel: **0.1.14-beta (78)**.

| Field | Value |
| --- | --- |
| APK | kabadiwala-connect-0.1.14-beta-debug.apk |
| Variant | envTestingDebug |
| Size | 3,19,14,789 bytes |
| SHA-256 | F46AB88915D7FB022458E90EDBB57D505039C7792811A2A37EC703EF97340619 |

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
$betaApk = Get-Item -LiteralPath 'backend/app-update/kabadiwala-connect-0.1.14-beta-debug.apk'
$betaApk.Length
(Get-FileHash -LiteralPath $betaApk.FullName -Algorithm SHA256).Hash
Get-Content -LiteralPath 'backend/app-update/update.json'
```

An upgrade needs the same application ID and signing key as the installed app. A higher version code does not fix a signing mismatch. Keep production signing credentials outside Git.

GitHub publication alone does not update a running backend. Deploy contracts before dependent clients. The **7:30 AM–9:30 PM** pickup window requires matching backend enforcement and Android presentation.

See [build instructions](../../README.md) and [verification status](../../docs/README.md). A successful APK build is not full runtime acceptance.
