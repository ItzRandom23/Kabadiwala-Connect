# AI and Dataset Card

AI features are assistive: image material suggestion and description drafting. Valuation, matching and anomaly flags are deterministic/rule-based. A collector must confirm classification and the recycler confirms material, weight and final value.

`AiInference` stores feature, provider, model version, input provenance (including image hash rather than image bytes), prediction, confidence, human correction, timestamp and explicit training-consent flag. Operational export separates this audit data from collector contact details.

Current source/size: development transactions and user-generated pilot records only; no governed training corpus is bundled. Known limitations include uneven classes, regional price variation, mixed-material images, uncertain weights, lighting/quality bias, and lack of measured accuracy. No production accuracy claim is permitted until a consented, deduplicated, anonymized holdout set reports per-class precision/recall, confusion matrix, coverage and calibration.

Cleaning pipeline: validate enum/unit/range → hash/deduplicate images → quarantine rejected records → remove unnecessary personal/location precision → stratify by class/location/time → version the snapshot → train/evaluate → retain correction lineage.

