# Privacy, Retention and Recovery Baseline

Collect only role, preferred language, broad operating location and transaction data needed for matching/traceability. Exact GPS, photos and contact details require a clear just-in-time purpose. Public QR verification never returns collector contact data.

Before production, configure retention periods by data class, authenticated account/image deletion, legal holds, anonymized research exports, encrypted backups and quarterly restore tests. Log authorization, price and payment changes without secrets. Private object storage must use short-lived signed access; public buckets and production local static uploads are rejected by configuration.

Incident requirement: the MongoDB and Gemini values formerly present in Git must be considered exposed until provider owners rotate/revoke them. Editing `.env.example` is repository containment, not credential rotation or history removal.

