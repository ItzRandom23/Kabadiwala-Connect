# Offline Material Passport

The prototype's Material Passport is a platform evidence timeline for a
material consignment. It is not a government certificate, waste authorisation,
chain-of-custody certificate or environmental-impact claim.

## Evidence chain

1. Household listing records material category, approximate weight, condition,
   area and optional photo reference.
2. Pickup completion records the assigned Kabadiwala, final measured weight,
   final price and a material-passport event.
3. Collector inventory and bulk/pool reservation identify the owner, grade,
   quantity and reservation movement.
4. A formal supply handover records a one-time QR/reference, quote, source
   (`POOL` or `BULK`), consignment hash, nonce hash, expiry, parties and
   location/evidence references.
5. The Collector confirms the prepared handover. The Recycler confirms the QR
   and supplies actual weight, accepted weight, material match, final rate,
   reason and evidence reference.
6. The platform creates a settlement breakdown and either completes the
   handover or marks it `REVIEW_REQUIRED`. The Collector can accept or raise an
   issue; the event and anomaly are retained.

`AuditEvent` and `MaterialPassportEvent` are written together for important
mutations. Evidence hashes cover event metadata; the prototype does not store
raw identity documents or claim immutable external notarisation.

## Pool allocation

Each opted-in Collector contributes from their own inventory. A pool only locks
after its minimum threshold is met. At receipt, the accepted weight is allocated
proportionally to reserved contribution quantity, and each contribution gets a
separate settlement row. Rejected quantity is released back to the contributor
when the settlement is accepted. Collector-to-collector sale or transfer is not
implemented.

## Demonstration boundary

The QR is a server-signed prototype payload (`kc-supply-handover-v1`) and is
displayed as a generated visual on Android. Seeded demand, rates and recycler
records must be labelled demo data. No field or official verification result is
represented by this document.

## Executed development fixture

The final emulator fixture completed a one-kilogram COPPER pooled handover at
the seeded Recycler rate of ₹105/kg. Backend readback showed `COMPLETED`, final
accepted quantity `1 kg`, final value `₹105`, a `COMPLETED` settlement and three
passport events. This is a development record, not a live price or economic
outcome. The Android flow verified the complete signed payload, scrolled to the
receipt controls, confirmed the material and showed `Receipt recorded`.
