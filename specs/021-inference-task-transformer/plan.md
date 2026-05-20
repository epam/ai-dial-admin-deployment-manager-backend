# Implementation Plan: Auto-Detected HuggingFace Inference Tasks with Chained Transformers

**Branch**: `021-inference-task-transformer` | **Date**: 2026-05-20 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/021-inference-task-transformer/spec.md`

## Summary

> **Revision 2 (2026-05-20)**: Detection runs at **deploy time only**. No persistence, no Flyway migration, no new API fields. The earlier persistence-based design described in the original revision is dropped; see [research.md §R-004](./research.md#r-004-detection-runs-at-deploy-time-no-persistence) for rationale.

When an inference deployment is deployed, `InferenceDeploymentManager.prepareServiceSpec(...)` invokes `InferenceTaskDetector.detect(...)` to fetch the model's HuggingFace metadata, decide whether the model is a text-classification model (via `pipeline_tag` or `*ForSequenceClassification` architectures), and read the model's `id2label` from `config.json`. The result flows directly into `InferenceManifestGenerator.serviceConfig(...)` and is never persisted. Deployments detected as `TEXT_CLASSIFICATION` produce a KServe `InferenceService` with a chained transformer block (image and resources from operator configuration), exposing a HuggingFace-API-shaped public endpoint. Non-classification deployments emit the unchanged predictor-only manifest. Forbidden operator-supplied predictor args (`--return_probabilities`, `--task=<non-sequence_classification>`) are rejected inside `applyChainedTransformer(...)` before any K8s mutation.

## Technical Context

**Language/Version**: Java 21 (constitution §Tech Stack)  
**Primary Dependencies**: Spring Boot 3.5.10, Lombok via `io.freefair.lombok` 8.10, MapStruct 1.6.0, Fabric8 Kubernetes Client 7.5.2 (KServe CRD generated), Flyway 11.14.0, Hibernate Envers, OkHttp (transitive — used by existing `HuggingFaceClient`), Jackson 2.21.1  
**Storage**: H2 / PostgreSQL / SQL Server (multi-vendor; existing `inference_deployment` table). **No schema changes** — detection is recomputed on every deploy.  
**Testing**: JUnit 5 + AssertJ; `./gradlew testFast` (H2) for dev, `./gradlew test` (full suite + testcontainers) for PR gate; ArchUnit `ArchitectureTest` for layering rules.  
**Target Platform**: Kubernetes cluster running KServe (`app.kserve.enabled=true`) — same as existing inference-deployments capability.  
**Project Type**: Web service (Spring Boot backend, single Gradle project).  
**Performance Goals**: HF metadata fetch on create/update — single network round-trip (model API + config.json fetch). Manifest generation latency unchanged. No new K8s-side performance targets.  
**Constraints**: Manager must reach `huggingface.co` (or operator-configured mirror via `HUGGINGFACE_BASE_URL`) at **deploy** time. HF unreachable → 5xx, no `InferenceService` created. Existing constitution rules apply: strict layering, `@LogExecution` on every component, MapStruct mappers in correct packages, Flyway owns schema (`ddl-auto: validate`), Apache Commons `StringUtils`/`CollectionUtils` for emptiness checks, 180-char line limit.  
**Scale/Scope**: Inference deployments are operator-created, low cardinality (tens-to-low-hundreds per cluster). HF API call cost is acceptable on the create/update path; not in a hot loop. No new background jobs.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Notes |
|---|---|---|
| I. Strict Layered Architecture | ✅ Pass | New code respects `web → service → dao + kubernetes` direction. Detection logic in `service/`. Persistence in `dao/`. K8s types only in `kubernetes/kserve/` (existing) and via Fabric8-generated `Transformer` types invoked from `service/manifest/`. |
| II. Transactional Discipline | ✅ Pass | `@Transactional` only on service/dao wrapper methods (e.g., create/update flow that combines HF fetch + persistence). No new `@Transactional` on controllers. |
| III. Kubernetes Isolation | ✅ Pass | Manifest generation continues to operate on Fabric8-generated KServe types from `service/manifest/`. K8s CRUD remains in `K8sKserveClient`. No new polling. |
| IV. Observability First | ✅ Pass | `@LogExecution` on every new `@Service` / `@Component` / `@Repository` / `@RestController` class. New detector adds structured log lines for HF fetch outcomes and detection decisions. |
| V. Security by Configuration | ✅ Pass | HF API token reuses existing `HUGGINGFACE_API_TOKEN` env var (no new secret). New env vars (`INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE`, resource overrides) follow `${ENV_VAR:default}` pattern in `application.yml`. |
| Naming Conventions | ✅ Pass | New classes: `InferenceTaskDetector` (service), `InferenceTaskDetectionResult` (record), `TextClassificationTransformerProperties` (@ConfigurationProperties), `TextClassificationTransformerSection` (service/manifest). No new persistence. |
| Code Style | ✅ Pass | New code follows Google Java Style + 180-char lines; Commons `StringUtils` / `CollectionUtils` for emptiness; no wildcard imports. |
| API Conventions | ✅ Pass | No new public endpoints. Modifies existing `POST/PATCH/GET /api/v1/deployments` for inference type. Errors flow through `DefaultExceptionHandler` → `ErrorView`. SpringDoc `@Operation`/`@ApiResponse` annotations updated on the inference handlers. |
| Testing Conventions | ✅ Pass | `*FunctionalTest` for H2/Postgres/SQL Server (vendor parity for the new columns); `*Test` for unit tests on detector + validators. AssertJ throughout. No mocking of K8s calls in functional tests. |
| Multi-Vendor Database | ✅ Pass | No new migration — feature does not change the schema. |
| Anti-Patterns (1-10) | ✅ Pass | No business logic in entities, no silent exception swallowing (HF client errors logged with context), no generic `Exception` catch outside `DefaultExceptionHandler`, no `@Transactional` on controllers, no K8s API calls from service layer (all via existing `K8sKserveClient`), no wildcard imports, no `System.out.println`, no hard-coded secrets, no K8s polling, `ddl-auto: validate` preserved. |
| Config docs | ✅ Pass | `docs/configuration.md` updated for new env vars in tasks.md. |
| Spec-kit Workflow | ✅ Pass | This is a numbered feature spec with `**Capability**:` line; capability specs (`inference-deployments`, `kubernetes-manifests`) will be updated on `Implemented` flip per the workflow rules. No per-feature `checklists/` directory committed (constitution §spec-kit Workflow Rules: deleted before commit; the requirements.md checklist used during specify/clarify is workflow-internal). |

**Constitution Check verdict**: ✅ All gates pass with no violations. No entries in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/021-inference-task-transformer/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions and rationale
├── data-model.md        # Phase 1 — entities, columns, validation
├── quickstart.md        # Phase 1 — operator walkthrough
├── contracts/
│   └── inference-deployment-api.md   # Phase 1 — REST changes
└── spec.md              # Existing — feature specification
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── model/deployment/
│   ├── InferenceDeployment.java                          # +detectedTask, +detectedId2Label fields
│   └── InferenceTask.java                                # NEW enum: TEXT_CLASSIFICATION | NONE
├── dao/
│   ├── entity/deployment/
│   │   └── InferenceDeploymentEntity.java                # +detected_task, +detected_id2label columns
│   └── mapper/
│       └── PersistenceInferenceDeploymentMapper.java     # If exists; otherwise add MapStruct attribute mappings
├── huggingface/
│   ├── client/
│   │   └── HuggingFaceClient.java                        # +getModel(modelName) + fetchConfigJson(modelName)
│   └── model/
│       └── ModelConfig.java                              # NEW: minimal config.json deserializer (pipeline_tag echo, architectures, id2label, num_labels)
├── service/
│   ├── deployment/
│   │   └── InferenceDeploymentService.java               # Existing service wrapper (or its equivalent); call detector at create/update
│   ├── detection/
│   │   └── InferenceTaskDetector.java                    # NEW: orchestrates HF fetch + classification + id2label extraction + validation
│   └── manifest/
│       ├── InferenceManifestGenerator.java               # +emit transformer block when detectedTask = TEXT_CLASSIFICATION
│       └── TextClassificationTransformerSection.java     # NEW: builds the transformer block (image, args, env, resources)
├── configuration/
│   └── TextClassificationTransformerProperties.java      # NEW: @ConfigurationProperties for image + resources
├── web/
│   ├── dto/deployment/
│   │   ├── InferenceDeploymentDto.java                   # +detectedTask, +detectedId2Label (read-only)
│   │   ├── CreateInferenceDeploymentRequestDto.java      # NO new fields; explicit doc that task/id2label not accepted
│   │   └── (existing source DTOs unchanged)
│   ├── mapper/
│   │   └── InferenceDeploymentDtoMapper.java             # +map new fields domain ↔ DTO (read-only in requests)
│   └── validation/
│       └── ForbiddenPredictorArgs.java + Validator       # NEW: bean-validation constraint on command/args for chained deployments
└── ...

src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/
└── V1.59__AddDetectedTaskAndId2LabelToInferenceDeployment.sql   # NEW

src/main/resources/
└── application.yml                                        # +app.inference.text-classification-transformer.{image,resources.*}

src/test/java/com/epam/aidial/deployment/manager/
├── service/detection/
│   └── InferenceTaskDetectorTest.java                    # NEW: unit tests for detection + id2label extraction + validation
├── service/manifest/
│   └── InferenceManifestGeneratorTest.java               # +cases for chained manifest
├── functional/{h2,postgres,sqlserver}/
│   └── InferenceDeploymentChainedFunctionalTest.java     # NEW: end-to-end vendor parity
└── web/validation/
    └── ForbiddenPredictorArgsValidatorTest.java          # NEW

docs/
├── configuration.md                                       # +new env vars
└── db-schema.md                                           # Regenerated via ./gradlew generateDbSchema
```

**Structure Decision**: Single Gradle project (existing layout). No new top-level directories. New code lands in established layer subpackages following the constitution's directory rules. The detector lives under `service/detection/` (a new subpackage; aligned with `service/deployment/`, `service/manifest/`, `service/pipeline/` precedent — each service "concern" gets its own subpackage when it owns multiple collaborators).

## Complexity Tracking

> No constitution violations to justify. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
