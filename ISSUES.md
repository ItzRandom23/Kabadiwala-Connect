# Open deployment work

The application and backend flows are implemented and covered by the local
automated checks. The remaining items are environment or release operations,
not placeholder UI paths:

- Provide a live MongoDB/Atlas database and run the Prisma preparation step.
- Configure production email/OTP delivery, FCM notifications, CameraX/ML Kit,
  object storage, payment provider, and any AI provider credentials.
- Supply a real HTTPS API URL and signed Android release credentials.
- Run device validation for camera permissions, QR scanning, GPS, TalkBack,
  network loss/recovery, and the target low-end devices.
- Rotate/revoke any API credential that was ever committed in repository
  history; removing it from the working tree does not invalidate that secret.

The release artifact generated locally is intentionally unsigned and uses the
explicit build-time endpoint supplied to Gradle.
