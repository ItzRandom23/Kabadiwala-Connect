# Firebase push setup

The repository now contains the FCM client/provider boundary, but push is deliberately disabled until a staging Firebase project and server credentials are provisioned. The in-app notification inbox remains available when push is disabled.

The Android client uses the Firebase BoM pinned in `android_app/gradle/libs.versions.toml`. The FCM service is registered only after Firebase is configured and the authenticated account has completed notification permission consent. The backend uses FCM HTTP v1 with a server-only service-account key.

## 1. Create separate Firebase projects

Create a staging project first. Add an Android app with the exact package name:

```text
com.irinteractivestudios.kabadiwalaconnect
```

Download `google-services.json` and place it at `android_app/app/google-services.json`. It is intentionally ignored by Git. The Android Gradle build applies the Google services plugin automatically only when this file exists, so local builds without Firebase configuration remain safe and functional.

Use a separate Firebase project and service account for production. Do not reuse staging credentials.

## 2. Provision the backend sender

Enable the Firebase Cloud Messaging API for the project and create a service account with the minimum permission required to send FCM messages. Store the service-account email and private key in the backend secret manager, never in Android resources or source control:

```env
NOTIFICATION_PUSH_PROVIDER=fcm
NOTIFICATION_PUSH_ENABLED=true
FCM_PROJECT_ID=<firebase-project-id>
FCM_CLIENT_EMAIL=<service-account-email>
FCM_PRIVATE_KEY=<private-key-with-literal-\n-escapes-or-secret-manager-newlines>
```

The backend exchanges the service-account assertion for a short-lived OAuth access token and calls the FCM HTTP v1 `messages:send` endpoint. It does not expose the key, access token, or raw provider error to the client.

## 3. Staging acceptance

1. Start the backend with the staging Firebase secrets and confirm `/api/v1/ready` is healthy.
2. Install a staging Android build containing the matching `google-services.json` and an explicit HTTPS testing API URL.
3. Sign in, grant Android 13+ notification permission, and confirm `POST /api/v1/notifications/devices` returns metadata only; provider tokens are never returned by the API.
4. Trigger one non-sensitive staging event and verify the `PUSH` outbox row and per-device target reach `SENT`.
5. Re-run the worker or restart the backend and confirm a sent target is not sent twice.
6. Revoke/uninstall the staging app or use an invalid token fixture and confirm FCM invalid-token handling disables only that device target.
7. Turn off `pushNotificationsEnabled` for the account and confirm a later event is recorded as `PUSH_NOT_OPTED_IN` without a provider call.

FCM delivery metrics, alerting, message-content review, retention, and launch approval remain operational release gates. Keep the provider disabled until the staging test and monitoring are complete.

Official references: [Firebase Android setup](https://firebase.google.com/docs/android/setup), [FCM Android client setup](https://firebase.google.com/docs/cloud-messaging/android/get-started), and [FCM HTTP v1 sending](https://firebase.google.com/docs/cloud-messaging/send/v1-api).
