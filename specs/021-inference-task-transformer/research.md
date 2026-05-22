# Phase 0 — Research

> **Revision 2 (2026-05-20)**: §R-004 (persistence shape) and §R-005 (lazy migration) from revision 1 are obsolete. Detection is now computed on every deploy with no persisted snapshot. §R-006 (HF fetch placement) and §R-007 (forbidden-args validation placement) move from create/update to deploy time. The rest is unchanged.

All decisions resolved; no `NEEDS CLARIFICATION` markers remain.

## R-001: HuggingFace metadata endpoints to drive detection

**Decision**: Use two HF Hub HTTP endpoints, both via the existing `HuggingFaceClient`:

1. `GET {HF_BASE}/api/models/{modelName}` → returns the model card metadata as JSON, including `pipeline_tag`, `tags`, `library_name`. Reuse the existing `Model` deserializer in `huggingface/model/`.
2. `GET {HF_BASE}/{modelName}/resolve/main/config.json` → returns the model's config. Reuse the existing `downloadFile` method on `HuggingFaceClient`.

Both endpoints honor the existing `HUGGINGFACE_API_TOKEN` env var. No new auth surface.

**Rationale**: Two signals (`pipeline_tag` and `architectures`) are both standard HF fields and what every HF Inference API integration uses. Reusing the existing client keeps the layering clean and inherits auth, base-url override, timeouts, and metrics for free.

**Alternatives considered**:
- Scraping the model card HTML — fragile, slow.
- Using only `pipeline_tag` — fails for models whose owners forgot to set the tag but emit `*ForSequenceClassification` architectures.
- Fetching `tokenizer_config.json` or `README.md` — adds latency without new signal.

---

## R-002: Detection rule

**Decision**:
```text
task = TEXT_CLASSIFICATION  iff  (pipeline_tag == "text-classification")
                              OR (architectures matches /^.*ForSequenceClassification$/)
otherwise task = NONE
```

When `task = TEXT_CLASSIFICATION`, `id2Label` is parsed from `config.json["id2label"]`. The map MUST:
- have keys parseable as non-negative integers,
- cover a dense range `{0, 1, …, n-1}` for some `n ≥ 1`,
- have non-empty string values,
- have no value matching the regex `^LABEL_\d+$` (HF's `AutoConfig` stub default).

Failure of any rule → reject the deploy with HTTP 400 listing the exact violation.

**Rationale**: Mirrors the reference transformer's runtime expectations so the manifest we generate is provably compatible. Stub-label rejection is the only way to prevent silent useless output.

**Alternatives considered**:
- Accepting stub labels as-is — produces a "working" deployment whose labels are `LABEL_0` / `LABEL_1`.
- Allowing sparse `id2label` — the transformer indexes positionally on predictor output; gaps silently shift labels.

---

## R-003: KServe `Transformer` CRD shape via Fabric8

**Decision**: Use the Fabric8-generated `io.kserve.serving.v1beta1.inferenceservicespec.Transformer` class. Mapping constants live in `utils/mapping/InferenceMappers.java`. The transformer block emits a single container named `kserve-container` (KServe convention).

**Rationale**: The existing manifest generator already uses `MappingChain` / `ListMapper` for complex nested CRDs. Reusing these primitives keeps the change localized.

---

## R-004: Detection runs at deploy time (no persistence)

**Decision**: Detection is computed inline inside `InferenceDeploymentManager.prepareServiceSpec(...)` on every deploy. The result is a transient `InferenceTaskDetectionResult` consumed immediately by `InferenceManifestGenerator.serviceConfig(...)` and then discarded. No database columns, no Flyway migration, no API DTO fields.

**Rationale (revision 2)**:
- The persistence design (revision 1) required HF reachability at deploy time anyway, for "lazy migration of pre-feature NULL rows" — so dropping persistence does not weaken the operational requirement.
- Removing persistence eliminates a Flyway migration across three DB vendors, two entity columns, a MapStruct conversion pair, audit-table changes, two API DTO fields, and the entire `applyTaskDetection` branch in `DeploymentService`.
- Staleness is no longer a concern: model owners changing `config.json` upstream propagates on the next deploy automatically.

**Trade-offs accepted**:
- HF Hub must be reachable at deploy time. Air-gapped clusters need an HF mirror or accept that chained-mode deploys are blocked. Same posture as the previous "lazy migration" code path.
- Forbidden-predictor-args validation moves from API boundary to deploy time. The K8s mutation is still gated before any side-effect, so the "no silent runtime failure" property is preserved.
- API consumers cannot preview the detected `task` / `id2Label` via GET. They can query HuggingFace directly if needed.

**Alternatives considered**:
- Persist detected fields (revision 1) — see git history for the previous spec text. Rejected because the simplification outweighed the staleness-protection / API-introspection benefits.
- Per-deploy in-memory cache — premature optimization; HF Hub is fast enough and operators do not deploy in tight loops.

---

## R-005: Lazy migration (obsolete)

Revision 2 makes this section moot. No persisted state exists for pre-feature rows; the deploy code path treats every deployment the same way.

---

## R-006: HF fetch placement in the deploy flow

**Decision**: `InferenceTaskDetector` is invoked from `InferenceDeploymentManager.prepareServiceSpec(...)`, which already runs at deploy time. The detector accepts a `HuggingFaceSource`, calls `HuggingFaceClient.getModel` / `fetchModelConfig`, validates per R-002, and returns an immutable `InferenceTaskDetectionResult`.

On HF transient error → propagate as a domain exception that `DefaultExceptionHandler` maps to 5xx. On model-not-found / unusable metadata → `InferenceTaskDetectionException`, mapped to 400. No partial K8s mutation occurs in either case (manifest generation throws before any `K8sKserveClient.create/updateService` call).

**Rationale**: Keeps detection in the service layer (Constitution Principle I), reuses `HuggingFaceClient` (Principle III: HF is not a K8s API). Single call site eliminates the multi-path complexity from revision 1.

**Alternatives considered**:
- Asynchronous detection — overkill at the expected call volume.
- Detection in create/update for early validation — reintroduces the persistence-or-cache problem we removed.

---

## R-007: Forbidden-args validation placement

**Decision**: Validation runs inside `InferenceManifestGenerator.applyChainedTransformer(...)` — the same site that injects the auto-required args. If the resolved predictor args contain `--return_probabilities` or `--task=<non-sequence_classification>`, the method throws `IllegalArgumentException` (mapped to HTTP 400) before any K8s mutation.

The validation only fires when detection has returned `TEXT_CLASSIFICATION`. Operators may freely use `--task=<other>` on non-text-classification deployments (e.g. `--task=translation` for a translation model — a legitimate use case that revision 1's create-time validation would have wrongly forbidden universally).

**Rationale**: Honors the spirit of [[feedback_chained_component_args_validation]] (block before K8s mutation, no silent runtime failures) while restoring legitimate use of `--task=<other>` for non-classification models.

**Alternatives considered**:
- Bean-validation annotation on the request DTO — requires knowing the topology at create time, which would force re-introducing detection at create time. Defeats the simplification.
- Universal rejection regardless of detection — wrongly blocks non-classification translation/text-generation models that legitimately use `--task=translation`.

---

## R-008: Configuration property layout (unchanged from revision 1)

**Decision**: `@ConfigurationProperties("app.inference.text-classification-transformer")`. Defaults live in `application.yml` via `${ENV_VAR:default}` syntax. Image has no default — operators must set it explicitly so it fails fast rather than silently using a development image.

---

## R-009: Status mapping with two components (unchanged from revision 1)

**Decision**: `InferenceDeploymentManager.mapStatus` checks both `components.predictor` and `components.transformer` (when present). Chained vs predictor-only is read from the live `InferenceService` (presence of a `transformer` block in the spec/status), not from any persisted field. This was already the case in revision 1 and remains correct after dropping persistence.

---

## R-010: Test strategy

**Decision**: Three layers:

1. **Unit tests** — `InferenceTaskDetector` (HF mocked; pipeline_tag match, architectures fallback, no match, missing id2label, sparse id2label, stub-label rejection, HF 404, HF 500); `InferenceManifestGenerator` for chained vs predictor-only; the forbidden-args path in `applyChainedTransformer`.
2. **Functional tests** — exercise the deploy code path with a mocked detector. No new schema means no migration test added.
3. **No new K8s integration** — existing inference deployment-flow integration coverage is sufficient.
