# Known Limitations

This remains a testing/SIH prototype. Material limitations are explicit:

- Rates, demand and some demo estimates can be seeded/development data; no
  live-price or savings guarantee is made.
- Recycler authorisation is a platform field verified by an operator/database,
  not an independently verified government result.
- Pool mutations and settlement require connectivity. Offline support covers
  cached formal evidence and a queued Recycler confirmation, not an offline
  shared-market replica.
- The QR is server-verified on confirmation; the prototype does not complete
  cryptographic verification fully offline.
- Human/admin resolution for disputed formal settlements is incomplete; a
  `REVIEW_REQUIRED` record is safer than silently forcing a payout, but still
  needs an operator workflow.
- Payment and earnings are not yet fully linked to every formal pool settlement;
  cash remains supported in the legacy transaction flow and the formal ledger
  has no complete admin resolution UI.
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
- Android critical supply-chain mutations remain online-only. The offline
  queue covers captured lot persistence, formal Recycler receipt confirmation
  and cached evidence; it is not a local shared-market replica.
- Critical live copy still has hardcoded English in places, pictorial/TTS
  coverage is incomplete, and a full TalkBack/native-speaker review remains.
- The emulator pass observed one cold-start Compose jank event; no battery,
  low-end-device or field-network performance claim is made.
