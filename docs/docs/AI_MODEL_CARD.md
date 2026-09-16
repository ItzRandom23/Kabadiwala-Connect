# AI Model Card

Status: assistive prototype integration only; no production model claim.

The existing application can request an image material suggestion or description
draft from the configured Gemini integration. The collector must confirm or
correct the result. Valuation, route advantage, pooling thresholds and anomaly
rules in the formalisation flow are deterministic; they do not depend on model
accuracy.

No benchmark dataset, per-class precision/recall, confusion matrix, calibration,
coverage, demographic analysis or safe deployment threshold has been produced.
Lighting, mixed materials, regional variation, image quality and class
imbalance are known risks. Do not present a suggestion as an identification,
weight measurement, price guarantee or hazardous-material clearance.

Before any production use, require a consented/deduplicated holdout set,
versioned prompts/model, human correction audit, failure review, privacy review,
rate/cost controls and a rollback path. Keep `AI_DATASET_CARD.md` and this card
synchronised with the actual provider and model version.
