# Security Threat Model — Testing/SIH Prototype

Scope: Android client, Express API, development MongoDB, role boundaries,
inventory mutations, formal supply handover and locally cached evidence.

| Threat | Control in this revision | Residual risk |
| --- | --- | --- |
| Household calls collector or Recycler APIs | Role middleware plus ownership predicates | Requires continued coverage as new routes are added |
| Expired Recycler authorisation buys material | `requireRecycler`, quote, handover and bulk-offer paths check validity window | Verification source is still operator/database supplied |
| Two operators open the same pool | Unique `PooledConsignment.sourceKey` plus P2002 convergence | Requires a database with the deployed schema |
| Oversell or double reservation | Conditional `updateMany` on available/reserved quantities inside transactions | Mongo transaction availability/configuration must be maintained |
| Replay/tampering of handover QR | HMAC signature, random nonce hash, server-bound reference/source, expiry and conditional status | HMAC secret rotation/operational secret management is not production-hardened |
| Recycler bypasses two-party confirmation | Receipt requires `COLLECTOR_CONFIRMED` | Legacy receipt endpoint intentionally returns a formal-handover conflict |
| Hidden deductions or material mismatch | Quote/final breakdown, accepted/rejected kg, reason/evidence, anomaly, Collector decision and audited admin accept/revert/release actions | External payment reversal and live transaction proof remain outside this revision |
| Collector identity/location leaks across roles | Recycler pool view aggregates contributions; collector listing/bulk views omit private IDs/coordinates | Some legacy/public endpoints need continued privacy review |
| Offline forged settlement | Role-aware `/sync` supports formal pool, settlement, handover, disposal-evidence and payment operations; server rechecks ownership, state, QR/nonce and current authorization | Cached UI is evidence, not authority, until server confirmation |
| Stale/duplicate client mutation | Operation IDs, conditional transitions and terminal-state replay handling | Not every legacy mutation has the same idempotency quality |

## Sensitive data rules

Do not commit passwords, API keys, Gemini credentials, MongoDB credentials,
identity documents, exact household addresses or raw research recordings. The
prototype uses development configuration only. Before any deployment, rotate
previously exposed provider credentials, use TLS, private storage, durable rate
limiting, audit retention policy, secret management, backups and a reviewed
incident response path.

## Verification performed

Backend build/lint/tests pass, QR round-trip and tamper tests pass. The
completed prototype pass exercised cross-role denial, expired/replayed QR,
duplicate pool creation, stale settlement, inventory conservation, formal
handover replay and pending Recycler verification through API/device fixtures.
The app also preserves local work across invalid-session recovery. These are
prototype assurance activities, not a production security certification.
