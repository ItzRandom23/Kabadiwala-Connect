# Known Limitations

This remains a testing/SIH prototype. Material limitations are explicit:

- Rates, demand and some demo estimates can be seeded/development data; no
  live-price or savings guarantee is made.
- Recycler authorisation is a platform field verified by an operator/database,
  not an independently verified government result.
- Shared pool discovery and multi-party coordination still require connectivity,
  but the backend accepts queued, role-aware offline operations for pool
  contribution/release, settlement decision, handover confirmation, disposal
  evidence and formal payment. This is not an offline shared-market replica.
- The QR is server-verified on confirmation; the prototype does not complete
  cryptographic verification fully offline.
- Internal admin resolution for formal anomalies supports accept-as-recorded,
  revert-to-quote, reservation release and acknowledgement with audit records.
  External bank/cash/provider reversal remains outside this repository.
- Formal pool settlements now feed the formal payment and Collector earnings
  projections; cash remains supported, but provider-level reversal and payout
  reconciliation are not implemented.
- Collector Growth Passport labels are platform-generated progress signals, not
  certification, credit scoring or income proof.
- Reverse Demand Network uses development/seeded demand and has no validated
  demand forecast or adoption evidence.
- Critical live supply-chain copy is not yet fully resource-localised across
  English, Hindi and Marathi. The safety acknowledgement and TTS paths need a
  native-speaker review.
- Hazardous-material guidance is a user warning, not a disposal licence or
  safety training substitute.
- No real field-research result is included. Follow the protocol and add
  anonymised findings only after consent.
- No production deployment, external competitor claim or environmental-impact
  number is supported.
- The development server creates JWT and traceability signing secrets per
  process for the local test harness. Restarting it invalidates previously
  issued test tokens/QRs; a real deployment must persist and rotate keys with
  explicit expiry and revocation policy.
- Android wiring does not yet expose every backend sync operation in the field
  UI. The backend queue supports captured lots, pool contribution/release,
  settlement decisions, formal Recycler confirmation, disposal evidence and
  formal payments; it is not a local shared-market replica.
- Critical live copy still has hardcoded English in places, pictorial/TTS
  coverage is incomplete, and a full TalkBack/native-speaker review remains.
- The emulator pass observed one cold-start Compose jank event; no battery,
  low-end-device or field-network performance claim is made.
