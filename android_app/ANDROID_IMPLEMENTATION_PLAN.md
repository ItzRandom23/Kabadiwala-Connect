# Android implementation plan — Neon Purple edition

Status: executed in this repository on 2026-09-15. The backend contract in `../docs/` is the source of truth. No backend or production deployment change was made.

## Work completed

1. Audited the existing Compose/MVVM app, Retrofit service, Room cache, WorkManager queue, role navigation and tests before changing code.
2. Replaced the warm/green visual layer with a dark-first Neon Purple Material 3 theme while retaining the existing architecture and role flows.
3. Expanded transport models and `ApiService` for household disposal evidence, pickup reliability/reassignment, inventory movements, offer lifecycle, demand lifecycle, pool suggestions, safety routing, passports and anomaly projections.
4. Added process-death-safe UUID idempotency keys for listing creation, pickup creation and formal handover confirmation. Offline pickup creation and recycler handover confirmation reuse the same key and payload through the existing Room/WorkManager queue.
5. Removed normal recycler receipt use of the legacy bulk-lot receive guard; recycler receipt remains the signed QR formal-handover flow.
6. Added the Android-specific disposal fields to the household listing wizard and kept safety guidance visible for hazardous categories.
7. Added endpoint coverage, visual-system and completion documentation.

## Follow-on implementation order

The remaining partial items are deliberately isolated so the working demo remains stable:

- Promote household detail/passport, pickup reschedule/settlement and anomaly/passport actions into dedicated detail destinations.
- Add collector offer reject/counter and recycler offer withdraw controls to the existing marketplace cards.
- Add a verified-recycler demand lifecycle editor and formal handover QC reason/evidence form to the current scan flow.
- Add a capability-gated Admin role/navigation surface using the already typed admin service methods.
- Replace the remaining legacy hardcoded screen copy with the existing resource/localization layer and complete Hindi translations.
- Add screenshot, accessibility, QR replay, Room migration and WorkManager instrumentation coverage on a configured emulator/device.

## Guardrails

- Role and ownership remain backend-enforced; local role state only controls navigation and does not authorize requests.
- Financial, inventory and handover success is shown only after a confirmed backend response.
- Cached evidence is visibly labelled and is never presented as a successful new mutation.
- `/recycler/bulk-lots/:lotId/receive` is exposed only as a compatibility contract and is not called by the normal recycler UI.
