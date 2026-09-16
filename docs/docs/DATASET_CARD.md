# Operational Dataset Card

Status: prototype operational schema; no governed training corpus is bundled.

## Sources and contents

- Development seed records for Household, Kabadiwala, Recycler, rates and
  procurement demand.
- User-generated testing records for listings, pickups, inventory, bulk lots,
  pools, handovers, settlements, audits and passport events.
- Optional AI inference records containing feature/model version, input
  provenance, confidence, correction and training-consent fields.

Exact home addresses, identity documents and research recordings are out of
scope. Export paths must apply the repository's privacy/retention policy.

## Intended use

Operational workflow testing, evidence-timeline demonstration, deterministic
route/pool calculations and future model evaluation planning. Rates and demand
are not a live market feed unless a source and freshness status are returned by
the API.

## Quality and governance

Validate enum/unit/range, hash and deduplicate image references, quarantine bad
records, minimise location precision, retain correction lineage, and separate
consented training data from operational data. There is no measured dataset
coverage, representativeness, accuracy or fairness result in this repository.
