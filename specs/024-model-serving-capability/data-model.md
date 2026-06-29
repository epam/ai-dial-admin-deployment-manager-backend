# Data Model: Model Serving Capability API

## Enum: `InferenceTask` (existing — extended)

`com.epam.aidial.deployment.manager.model.deployment.InferenceTask`

| Value | Meaning | FE consumption surface |
|---|---|---|
| `TEXT_GENERATION` | **(new)** Generative / causal-LM model | chat completion endpoint |
| `TEXT_CLASSIFICATION` | Sequence-classification model | MCP toolset |
| `NONE` | No recognised chained task | neither |

- `TEXT_GENERATION` is purely informational for the capability API; it does **not** add a transformer
  to the generated manifest (FR-009 — predictor-only manifest shape preserved, same as `NONE`).

## Domain model: `InferenceDeployment` (existing — extended)

`com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment`

| Field | Type | Notes |
|---|---|---|
| `inferenceTask` | `InferenceTask` | **(new)** System-computed. Null in-memory before enrichment; persisted on create/update. |

## Entity: `InferenceDeploymentEntity` (existing — extended)

`com.epam.aidial.deployment.manager.dao.entity.deployment.InferenceDeploymentEntity`

| Column | Type | Constraints | Mapping |
|---|---|---|---|
| `inference_task` | `VARCHAR` | nullable | `@Enumerated(EnumType.STRING)`, `@Column(name = "inference_task")` |

- Entity is `@Audited` (Envers) — new column is tracked in the audit table automatically.
- Pure data holder — no logic in the entity (anti-pattern #1).

## Migration: `V1.59__AddInferenceTaskColumn.sql`

Authored per vendor under `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`.

```sql
ALTER TABLE inference_deployment ADD inference_task VARCHAR(255);
```

- Nullable; no backfill. Null is coalesced to `NONE` at the API boundary (FR-007).
- Adjust per-vendor `ADD` syntax as needed (`ADD COLUMN` for H2/Postgres, `ADD` for SQL Server).
- After authoring, run `./gradlew generateDbSchema` and commit `docs/db-schema.md`.

## State / lifecycle

- **Create**: enrichment hook runs detection on the HuggingFace source → sets `inferenceTask` → saved.
- **Update (source changed)**: re-run detection → update `inferenceTask` (FR-004). Update that does
  not change the model source leaves the value intact.
- **Deploy**: `prepareServiceSpec` continues to call `detect()` for the manifest/`id2Label`; the
  persisted `inferenceTask` is consistent with it (same detector).
- **Read**: serialised to `InferenceDeploymentDto.inferenceTask`; null → `NONE`.

## Validation rules

- `inferenceTask` is system-computed only; rejected/ignored if supplied on create/update (FR-008) —
  the field is simply not present on `CreateInferenceDeploymentRequestDto`.
- Detection failure (model not found, metadata missing/unusable, HF Hub unreachable) fails the
  create/update operation, unchanged from today (spec edge cases).
