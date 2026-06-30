# Research: Model Serving Capability API

Phase 0 — resolve open decisions before design. Three decisions (API surface, compute timing,
response shape) were already settled with the user during `/speckit.specify`; recorded here for the
record plus the new technical decisions.

## Decision 1: How to detect text-generation

**Decision**: Detect text-generation from the HuggingFace `pipeline_tag == "text-generation"`, with a
`config.json` architecture fallback matching causal-LM / generative architectures (e.g. names ending
in `ForCausalLM`, `ForConditionalGeneration`, `LMHeadModel`). Mirror the existing two-signal pattern
already used for text-classification (`pipeline_tag` OR `*ForSequenceClassification` architecture).

**Rationale**: The detector already fetches both `Model.pipelineTag` and `ModelConfig.architectures`
for every model (see `InferenceTaskDetector.detect`). Reusing them adds zero new HF calls and keeps a
single linear pass. `pipeline_tag` is the authoritative HF signal; the architecture fallback covers
models that omit the tag.

**Alternatives considered**:
- *Tag-only* (no architecture fallback): simpler but misses models with an unset `pipeline_tag`.
- *New HF endpoint / model card parsing*: rejected — new external dependency, against the "no new
  integration" assumption.

## Decision 2: Precedence when a model matches more than one task (FR-002a)

**Decision**: **Text-classification takes precedence over text-generation.** Evaluate the
sequence-classification signal first; only if it does not match, evaluate the text-generation signal;
otherwise `NONE`.

**Rationale**: A `*ForSequenceClassification` architecture (or `text-classification` pipeline tag) is
the more specific, higher-confidence signal and already drives the transformer-chaining contract in
spec 021. Some checkpoints carry generative tags alongside a classification head; classifying those as
TEXT_CLASSIFICATION preserves existing 021/022 manifest behaviour (FR-009) and avoids regressions.

**Alternatives considered**:
- *Generation-first*: would reclassify existing text-classification models and break the 021
  transformer chain — rejected.
- *Error on ambiguity*: rejected — detection must yield exactly one value (FR-002) and must not fail a
  model that works today.

## Decision 3: Where to compute and persist (FR-003, FR-004)

**Decision**: Compute and persist the task at **create** and on **source-changing update**, via a
type-specific enrichment hook on the deployment manager, invoked by `DeploymentService` just before
`deploymentRepository.save`. The deploy-time `prepareServiceSpec` path is left calling `detect()` for
the manifest/`id2Label` as today (it needs `id2Label`, which is not persisted); since both use the
same detector they agree.

**Rationale**: Honors the user's "persist at create/deploy time" choice and makes the value available
the moment a deployment exists — so the API field (Decision 4) is correct even before first deploy
(option-1 "without caveats"). Keeping the hook in `InferenceDeploymentManager` (type-specific) rather
than in the generic `DeploymentService` respects layered/type isolation: HF detection logic does not
leak into the generic CRUD path. A default no-op hook on the `DeploymentManager` interface keeps other
deployment types unaffected.

**Trade-off (accepted)**: Detection runs at create/update *and* again at deploy (one extra HF Hub call
per deploy, same as today's deploy behaviour). Documented; not optimised now.

**Alternatives considered**:
- *Persist only at deploy time*: simplest, but a created-but-never-deployed deployment would report
  `NONE` until first deploy — weaker than the user's create-time intent. Rejected.
- *Persist `id2Label` too and read everything back at deploy*: removes the double call but stores a
  larger structure and widens scope (this feature is about the capability type only, FR-006).
  Deferred as a future optimisation.

## Decision 4: API field name, type, and absent-value handling (FR-005, FR-006, FR-007)

**Decision**: Expose a read-only field `inferenceTask` on `InferenceDeploymentDto` of enum type
`InferenceTask` with values `TEXT_GENERATION` | `TEXT_CLASSIFICATION` | `NONE`. The column is created
as nullable; rows predating this feature (and any not-yet-enriched row) read as **`NONE`** at the API
boundary (mapper coalesces null → `NONE`). No endpoint URL or path is returned — the frontend owns the
mapping.

**Rationale**: Reuses the existing domain enum and DTO/mapper plumbing (no new types, matches naming
conventions). Coalescing null → `NONE` gives the frontend a single non-null value to switch on (FR-007)
without a schema backfill migration. Read-only: the field is absent from `CreateInferenceDeploymentRequestDto`,
so clients cannot set it (FR-008).

**Alternatives considered**:
- *Name it `capability` / `servingCapability`*: more FE-friendly, but diverges from the established
  `InferenceTask` domain term and the manifest contract. Rejected for consistency; FE owns the label.
- *Backfill column with `NONE` via migration*: unnecessary — null-coalescing in the mapper covers it
  and avoids touching existing rows.

## Decision 5: Migration & persistence mechanics

**Decision**: New Flyway migration `V1.59__AddInferenceTaskColumn.sql` in all three vendor dirs
(H2, POSTGRES, MS_SQL_SERVER), adding a nullable `inference_task VARCHAR` column to
`inference_deployment`. Map with JPA `@Enumerated(EnumType.STRING)`. The entity is `@Audited`
(Envers) — the new column joins the audit table automatically; verify the Envers audit table picks it
up. Run `./gradlew generateDbSchema` and commit the updated `docs/db-schema.md`.

**Rationale**: Highest existing migration is `V1.58`; `V1.59` is next. STRING enum mapping is
human-readable and stable across reordering. Nullable avoids a backfill (see Decision 4).

**Alternatives considered**:
- *Ordinal enum mapping*: rejected — brittle if enum order changes.
- *Single vendor-agnostic migration*: not possible under the project's per-vendor migration pattern.

## Open items

None blocking. The `id2Label`-persistence optimisation (Decision 3) is explicitly deferred.
