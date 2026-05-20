# Phase 1 — Data Model

> **Revision 2 (2026-05-20)**: This feature was originally specified with persisted `detected_task` and `detected_id2label` columns. The shipped implementation drops persistence entirely — detection is recomputed on every deploy. This file documents the revised model. The earlier persistence-based design is preserved in git history only.

## Domain entities

### `InferenceTask` (new enum)

Package: `com.epam.aidial.deployment.manager.model.deployment`

```text
enum InferenceTask {
    TEXT_CLASSIFICATION,   // model is sequence-classification; chained manifest emitted
    NONE                   // model is not sequence-classification; predictor-only manifest
}
```

Used only as a transient computation result; never persisted, never serialized in any REST DTO.

### `InferenceDeployment` (unchanged)

No new fields. The existing `modelFormat` and `source` already give the system everything it needs to drive detection at deploy time.

## REST DTOs (unchanged)

`CreateInferenceDeploymentRequestDto` / `InferenceDeploymentDto`: **no new fields**. The detection result is never exposed to clients. The DTOs do NOT accept `task` or `id2label`. Clients that need to preview a model's labels can query HuggingFace directly.

## Persistence (unchanged)

- `InferenceDeploymentEntity`: no schema changes — no new columns.
- `PersistenceDeploymentMapper`: no new field mappings.
- No Flyway migration ships with this feature.
- No audit-table (`inference_deployment_aud`) changes.

## Detection result type

### `InferenceTaskDetectionResult` (new record)

Package: `com.epam.aidial.deployment.manager.service.detection`

```text
public record InferenceTaskDetectionResult(
    InferenceTask task,
    @Nullable Map<Integer, String> id2Label    // non-null iff task == TEXT_CLASSIFICATION
) {
    public static InferenceTaskDetectionResult none();
    public static InferenceTaskDetectionResult textClassification(Map<Integer, String> id2Label);
}
```

Returned by `InferenceTaskDetector.detect(HuggingFaceSource)`. Immutable; consumed inline by `InferenceDeploymentManager.prepareServiceSpec()`; never persisted.

Detection invariants (enforced inside `InferenceTaskDetector`):
- `task = TEXT_CLASSIFICATION` ↔ `id2Label != null && !id2Label.isEmpty()`
- `task = NONE` ↔ `id2Label == null`
- `id2Label.keySet()` is exactly `{0, 1, …, size-1}` when present
- No value in `id2Label` is null, empty, blank, or matches `^LABEL_\d+$`

## State transitions

| Event | Behavior |
|---|---|
| Create / update inference deployment | No detection. The deployment is stored as-is. Detection is deferred to deploy. |
| Deploy | `InferenceDeploymentManager.prepareServiceSpec` calls `InferenceTaskDetector.detect(source)` against the HF Hub. The result flows directly into `InferenceManifestGenerator.serviceConfig(...)` and is then discarded. |
| Redeploy after model swap | Detection runs again against the new model. Topology flips automatically if the task category changes. |
| Import / export | No detected fields participate. Existing deployment fields round-trip as before. |

## Configuration entity (unchanged from revision 1)

### `TextClassificationTransformerProperties`

Package: `com.epam.aidial.deployment.manager.configuration`

```text
@ConfigurationProperties("app.inference.text-classification-transformer")
@Data
class TextClassificationTransformerProperties {
    String image;          // no default — must be set when chained deployments exist
    Resources resources;

    @Data
    static class Resources {
        String cpuRequest;
        String cpuLimit;
        String memoryRequest;
        String memoryLimit;
    }
}
```

Defaults live in `application.yml` (constitution Patterns: Configuration property defaults).

## Forbidden-args list

Enforced inside `InferenceManifestGenerator.applyChainedTransformer(...)` at deploy time (not at API boundary, because the topology is unknown until detection runs):

- `--return_probabilities` — conflicts with the auto-injected `--return_raw_logits`.
- `--task=<value>` for any `<value>` other than `sequence_classification`.

The check iterates the predictor's resolved `args` list and rejects with `IllegalArgumentException` (mapped to HTTP 400) before any K8s mutation.

## Out of scope

- No persistence, no migration, no audit changes.
- No new repository queries, list endpoints, or cleanup logic.
