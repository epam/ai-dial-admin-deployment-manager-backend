# Tasks: Auto-Detected HuggingFace Inference Tasks with Chained Transformers

> **Revision 2 (2026-05-20)**: Persistence is dropped from this feature. The following task groups are **obsolete** in the shipped design and were rolled back: the Flyway migrations T002–T004, the entity and mapper edits that added persisted columns, the API-DTO read-only fields, the bean-validation `@ForbiddenPredictorArgs` constraint, and the `applyTaskDetection` call site inside `DeploymentService`. Detection now runs inline inside `InferenceDeploymentManager.prepareServiceSpec(...)` on every deploy. See [spec.md](./spec.md) "Session 2026-05-20 (revision 2)" and [research.md §R-004](./research.md#r-004-detection-runs-at-deploy-time-no-persistence) for rationale. The historical task list below is preserved for traceability; only items whose subject still exists in the codebase (the detector, the manifest section, the configuration properties class, the HuggingFace client additions) remain in force.

**Input**: Design documents from `/specs/021-inference-task-transformer/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. The constitution mandates testing patterns (unit + multi-vendor functional); research.md §R-010 codifies the test strategy.

**Organization**: Tasks are grouped by user story. Each story is independently implementable and testable per the spec's Independent Test criteria.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps to a user story in spec.md (US1–US6); Setup/Foundational/Polish phases have no story label
- Each task lists the exact file path(s) it touches

## Path Conventions

Single Gradle project (Spring Boot backend). Source under `src/main/java/com/epam/aidial/deployment/manager/`, tests under `src/test/java/...`. Migrations under `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`. Per the constitution.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm working environment for the feature branch.

- [X] T001 Verify branch `021-inference-task-transformer` is checked out and the working tree is clean before starting; abort if not. (No code changes — sanity check before mutations.)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, enum, and configuration scaffolding required by every user story.

**⚠️ CRITICAL**: No user-story work can begin until this phase is complete.

- [X] T002 [P] Create Flyway migration `src/main/resources/db/migration/H2/V1.59__AddDetectedTaskAndId2LabelToInferenceDeployment.sql` — adds nullable `detected_task VARCHAR(64)` and `detected_id2label JSON` columns to `inference_deployment` AND to the Envers audit table (`inference_deployment_aud` — verify exact name in `V1.55__CreateAuditTables.sql`). Per data-model.md §"Flyway migration".
- [X] T003 [P] Create Flyway migration `src/main/resources/db/migration/POSTGRES/V1.59__AddDetectedTaskAndId2LabelToInferenceDeployment.sql` — same shape as the H2 variant; `JSON` column type.
- [X] T004 [P] Create Flyway migration `src/main/resources/db/migration/MS_SQL_SERVER/V1.59__AddDetectedTaskAndId2LabelToInferenceDeployment.sql` — `NVARCHAR(64)` and `NVARCHAR(MAX) NULL` per data-model.md.
- [X] T005 [P] Create enum `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceTask.java` with values `TEXT_CLASSIFICATION`, `NONE`.
- [X] T006 [P] Create `src/main/java/com/epam/aidial/deployment/manager/configuration/TextClassificationTransformerProperties.java` — `@ConfigurationProperties("app.inference.text-classification-transformer")` per data-model.md; fields uninitialized (constitution Patterns: Configuration property defaults).
- [X] T007 Add config defaults to `src/main/resources/application.yml` under the `app.inference.text-classification-transformer` key: `image: ${INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE:}`, plus the four `resources.*` properties with defaults `100m`/`500m`/`256Mi`/`512Mi` per research.md §R-008.
- [X] T008 [P] Update `docs/configuration.md` documenting `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` (no default; required for chained deploys) and the four `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_{CPU,MEMORY}_{REQUEST,LIMIT}` env vars with their defaults and descriptions.

**Checkpoint**: schema + enum + config in place. User-story phases may proceed.

---

## Phase 3: User Story 1 — Deploy a HuggingFace text-classification model with zero ceremony (Priority: P1) 🎯 MVP

**Goal**: An operator creates an inference deployment with only `modelName`; on deploy, a chained `InferenceService` is emitted with predictor + transformer; the public URL serves HF-API-shaped responses with the model's own labels.

**Independent Test**: `POST /api/v1/deployments` with a known text-classification HF model (e.g. `distilbert-base-uncased-finetuned-sst-2-english`); response carries `detectedTask=TEXT_CLASSIFICATION` and `detectedId2Label={0:"NEGATIVE",1:"POSITIVE"}`. Deploy; generated manifest has predictor + transformer; predictor args include `--return_raw_logits` and `--task=sequence_classification`; transformer container has `ID2LABEL` env var and the configured image.

### HuggingFace client extensions

- [X] T009 [US1] Add `Model getModel(String modelName)` to `src/main/java/com/epam/aidial/deployment/manager/huggingface/client/HuggingFaceClient.java` — calls `GET {baseUrl}/api/models/{modelName}` and deserializes into the existing `Model` record; honors `HUGGINGFACE_API_TOKEN` like the existing methods. Throw `HuggingFaceClientException` on non-2xx.
- [X] T010 [US1] Extend `src/main/java/com/epam/aidial/deployment/manager/huggingface/model/Model.java` with `pipelineTag` (Jackson alias `pipeline_tag`) and `architectures` fields if not already present.
- [X] T011 [P] [US1] Create `src/main/java/com/epam/aidial/deployment/manager/huggingface/model/ModelConfig.java` — record/POJO deserializer for the relevant `config.json` fields: `architectures` (List<String>), `id2label` (Map<String, String>), `numLabels` (Jackson alias `num_labels`).
- [X] T012 [US1] Add `ModelConfig fetchModelConfig(String modelName)` to `HuggingFaceClient` — calls `GET {baseUrl}/{modelName}/resolve/main/config.json` (reuse `buildFileUrl` pattern) and deserializes into `ModelConfig`. Throw `HuggingFaceClientException` on non-2xx.

### Detector

- [X] T013 [P] [US1] Create `src/main/java/com/epam/aidial/deployment/manager/service/detection/InferenceTaskDetectionResult.java` — record `(InferenceTask task, @Nullable Map<Integer, String> id2Label)`.
- [X] T014 [US1] Create `src/main/java/com/epam/aidial/deployment/manager/service/detection/InferenceTaskDetector.java` (+ co-located `InferenceTaskDetectionException`) — `@Component @LogExecution`; method `InferenceTaskDetectionResult detect(HuggingFaceSource source)` that:
  1. calls `huggingFaceClient.getModel(...)`;
  2. checks `pipeline_tag == "text-classification"` OR any architecture matching `.*ForSequenceClassification$`;
  3. if classification → calls `fetchModelConfig(...)` and parses `id2label` into `Map<Integer, String>` (LinkedHashMap, ascending by key);
  4. returns the result.
  Pure happy-path here; validation and error mapping in US3.

### Domain + persistence wiring

- [X] T015 [P] [US1] Add fields `private InferenceTask detectedTask;` and `private Map<Integer, String> detectedId2Label;` to `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceDeployment.java`.
- [X] T016 [P] [US1] Add columns `detectedTask` (String, mapped via `@Enumerated(EnumType.STRING)` or string column + manual conversion) and `detectedId2Label` (String JSON, mapped via `@JdbcTypeCode(SqlTypes.JSON)`) to `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/InferenceDeploymentEntity.java`. Verify the project's existing JSON-column pattern (used by `PersistenceSource`) before choosing the exact annotation.
- [X] T017 [US1] Update the InferenceDeployment persistence mapper (find via `Glob` for `PersistenceInferenceDeploymentMapper` or attribute mappings on the base `PersistenceDeploymentMapper`) to map domain `Map<Integer, String>` ↔ entity `String` (JSON via Jackson) and domain `InferenceTask` ↔ entity string. New MapStruct `@Named` helpers may be required.
- [X] T018 [P] [US1] Add fields `detectedTask` and `detectedId2Label` to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/InferenceDeploymentDto.java` — annotated `@Schema(accessMode = AccessMode.READ_ONLY)` per contracts/inference-deployment-api.md.
- [X] T019 [US1] Update `InferenceDeploymentDtoMapper` (handled via `DeploymentDtoMapper.setInferenceDtoSource` AfterMapping) (web mapper) to round-trip the two new fields domain ↔ DTO. The MapStruct mapper is in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/`.

### Service-layer detection hook (create path)

- [X] T020 [US1] In the inference-deployment creation flow (`DeploymentService.createDeployment` and `.updateDeployment` — new `applyTaskDetection` helper; detection re-runs only when modelName changes per US5 logic) (start at `web/controller/DeploymentController` and follow into the service that handles INFERENCE type; verify the exact service name — likely `DeploymentService` or `InferenceDeploymentService`), after validating the request and before persisting, call `InferenceTaskDetector.detect(source)` and set the resulting `detectedTask` and `detectedId2Label` on the domain object. Detection runs inside the existing `@Transactional` boundary per research.md §R-006.

### Manifest generation — chained transformer

- [~] T021 [P] [US1] SKIPPED: built `TextClassificationTransformerSection` using Fabric8 POJO setters directly instead of `MappingChain` constants — simpler and avoids generic boilerplate. Add to `InferenceMappers` later only if a second consumer needs the chain. to `src/main/java/com/epam/aidial/deployment/manager/utils/mapping/InferenceMappers.java` per research.md §R-003: `SERVICE_SPEC_TRANSFORMER_FIELD`, `TRANSFORMER_CONTAINERS_FIELD` (list by `name`), `TRANSFORMER_CONTAINER_ENV_FIELD`, `TRANSFORMER_CONTAINER_ARGS_FIELD`, `TRANSFORMER_CONTAINER_RESOURCES_FIELD`, `TRANSFORMER_CONTAINER_IMAGE_FIELD`. Use the existing `MappingChain`/`ListMapper` primitives.
- [X] T022 [US1] Create helper `src/main/java/com/epam/aidial/deployment/manager/service/manifest/TextClassificationTransformerSection.java` — `@Component @LogExecution`; method `void apply(MappingChain<InferenceService> config, String deploymentName, Map<Integer, String> id2Label, TextClassificationTransformerProperties props)` that builds the `kserve-container` transformer block: image, args (`--model_name=<name>`, `--predictor_protocol=v2`), env (`ID2LABEL = jackson.writeValueAsString(id2Label)`), resources from properties.
- [X] T023 [US1] Modify `src/main/java/com/epam/aidial/deployment/manager/service/manifest/InferenceManifestGenerator.java` — add a new overload (or extend `serviceConfig`) that accepts `InferenceTask task`, `Map<Integer, String> id2Label`, `TextClassificationTransformerProperties transformerProps`. When `task = TEXT_CLASSIFICATION`:
  1. Set `protocolVersion: v2` on the predictor block.
  2. Auto-inject `--return_raw_logits` and `--task=sequence_classification` into predictor args (always — per FR-014, merge on top of operator-supplied args; do not check `isArgPresent` for these two).
  3. Call `TextClassificationTransformerSection.apply(...)`.
  When `task = NONE`, behavior is unchanged.

### Wire manifest at deploy time

- [X] T024 [US1] Modify `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java` `prepareServiceSpec(...)` — pass `deployment.getDetectedTask()`, `deployment.getDetectedId2Label()`, and the injected `TextClassificationTransformerProperties` into the manifest generator. Constructor adds a `TextClassificationTransformerProperties` dependency.

### Status + URL resolution for chained deployments

- [X] T025 [US1] Update `InferenceDeploymentManager.mapStatus(...)` per research.md §R-009: when `components.transformer` exists in the InferenceService status, status is `RUNNING` only when BOTH predictor and transformer report `Loaded + UpToDate`. Failure in either → `CRASHED` via existing rules.
- [X] T026 [US1] Update `InferenceDeploymentManager.resolveServiceUrl(...)` per research.md §R-009: when `components.transformer` is present, return its URL in preference to predictor's. Otherwise unchanged.

### Tests for US1

- [X] T027 [P] [US1] Unit tests for `InferenceTaskDetectorTest` (8 cases: pipeline_tag + architecture happy paths, NONE detection, missing/sparse/non-integer/stub-only/empty-value id2label rejections) in `src/test/java/com/epam/aidial/deployment/manager/service/detection/InferenceTaskDetectorTest.java` — happy paths: `pipeline_tag = text-classification`, `architectures = [DistilBertForSequenceClassification]`, both-set. Mock `HuggingFaceClient`. AssertJ assertions.
- [ ] T028 [P] [US1] DEFERRED: Unit tests for chained-mode in `InferenceManifestGeneratorTest` extending the existing test class in `src/test/java/com/epam/aidial/deployment/manager/service/manifest/InferenceManifestGeneratorTest.java` — assert chained-mode shape: presence of `transformer` block, image value, `ID2LABEL` env var, predictor `--return_raw_logits` + `--task=sequence_classification`, predictor `protocolVersion: v2`, unchanged shape when `task = NONE`.
- [ ] T029 [US1] DEFERRED: Functional test (H2) `src/test/java/com/epam/aidial/deployment/manager/functional/h2/InferenceDeploymentChainedFunctionalTest.java` — `POST /api/v1/deployments` for a text-classification model (use a mock HF server with fixed responses), assert response has `detectedTask=TEXT_CLASSIFICATION`, `detectedId2Label` populated; deploy; inspect captured KServe `InferenceService` (via the existing fake/in-memory KServe client) for chained shape.
- [ ] T030 [P] [US1] DEFERRED: Functional-test variants for Postgres and SQL Server: `src/test/java/com/epam/aidial/deployment/manager/functional/postgres/InferenceDeploymentChainedFunctionalTest.java` and `.../sqlserver/InferenceDeploymentChainedFunctionalTest.java`, each extending the appropriate `*FunctionalTests` base. Validates the JSON column round-trip per vendor.

**Checkpoint**: US1 is independently shippable. MVP-complete.

---

## Phase 4: User Story 2 — Non-text-classification models continue as predictor-only (Priority: P1)

**Goal**: A non-classification model (translation, summarization, LLM, etc.) produces a predictor-only manifest. Existing legacy rows (created before this feature) deploy successfully by running detection lazily.

**Independent Test**: Create an inference deployment with `modelName` of a non-classification model (e.g. `Helsinki-NLP/opus-mt-en-de`). `detectedTask = NONE`, `detectedId2Label = null`. Deploy; manifest has only `predictor` block. Separately: take a legacy row (NULL `detected_task`); deploy it; row is updated with the detected values; manifest reflects them.

- [ ] T031 [US2] Confirm `InferenceTaskDetector.detect(...)` returns `(NONE, null)` when neither `pipeline_tag = text-classification` nor any architecture matches `*ForSequenceClassification`. If T014 already covers this, no code change; otherwise add the branch.
- [ ] T032 [US2] Lazy-migration hook in `InferenceDeploymentManager.prepareServiceSpec(...)`: if `deployment.getDetectedTask() == null`, call `InferenceTaskDetector.detect(source)` inline, persist the result via the existing repository, then proceed with manifest generation. Use a small helper rather than embedding the logic in `prepareServiceSpec`.
- [ ] T033 [P] [US2] Unit tests in `InferenceTaskDetectorTest` for non-classification cases: `pipeline_tag = translation`, `pipeline_tag = null` + non-matching architectures, `architectures = null`.
- [ ] T034 [US2] Functional test (H2 + 1 testcontainer variant) `src/test/java/com/epam/aidial/deployment/manager/functional/h2/InferenceDeploymentPredictorOnlyFunctionalTest.java` — creates a non-classification deployment and asserts the manifest has no `transformer` block; separately seeds a row with `detected_task = NULL`, deploys it, and asserts the row is updated and the manifest is correct.

**Checkpoint**: US2 stands alone. Backward compatibility verified.

---

## Phase 5: User Story 3 — Refuse to deploy when model metadata is unusable (Priority: P1)

**Goal**: Every unusable-metadata case (missing id2label, sparse keys, stub labels, model not found, HF unreachable, forbidden predictor args) is rejected at the API boundary with the right HTTP status and an actionable message.

**Independent Test**: Submit create requests that hit each failure case; each returns the expected status + message; no DB rows or cluster mutations occur.

### Typed exceptions + validation

- [ ] T035 [P] [US3] Create `src/main/java/com/epam/aidial/deployment/manager/service/detection/InferenceTaskDetectionException.java` — hierarchy:
  - `InferenceTaskDetectionException` (abstract base; carries `modelName` and `reason`)
  - `ModelMetadataMissingException` (id2label missing / empty)
  - `ModelMetadataUnusableException` (sparse keys / stub labels / empty values / non-integer keys)
  - `ModelNotFoundException` (HF 404)
  - `HuggingFaceUpstreamException` (HF 5xx / network / timeout)
- [ ] T036 [US3] In `InferenceTaskDetector.detect(...)`, when `task = TEXT_CLASSIFICATION`, validate the `id2label` map per research.md §R-002:
  - parse keys with `Integer.parseUnsignedInt`; non-integer → `ModelMetadataUnusableException`
  - reject empty map / null entries → `ModelMetadataMissingException`
  - reject if key set is not exactly `{0, 1, …, n-1}` → `ModelMetadataUnusableException`
  - reject empty/blank values → `ModelMetadataUnusableException`
  - reject all-stub values (`^LABEL_\d+$` for every entry) → `ModelMetadataUnusableException`
- [ ] T037 [US3] In `InferenceTaskDetector.detect(...)`, translate `HuggingFaceClientException` based on HTTP status: 404 → `ModelNotFoundException`; 401/403 → `ModelNotFoundException` with auth hint; 5xx / network → `HuggingFaceUpstreamException`.
- [ ] T038 [US3] Add exception handlers in `src/main/java/com/epam/aidial/deployment/manager/web/handler/DefaultExceptionHandler.java`:
  - `ModelNotFoundException` / `ModelMetadataMissingException` / `ModelMetadataUnusableException` → `400` with the canonical message from contracts/inference-deployment-api.md.
  - `HuggingFaceUpstreamException` → `503` (verify final HTTP code with the existing convention for upstream failures — there may already be a precedent in this handler).

### Forbidden-args validator

- [ ] T039 [P] [US3] Create constraint annotation `src/main/java/com/epam/aidial/deployment/manager/web/validation/ForbiddenPredictorArgs.java` (class-level, message resource, default groups).
- [ ] T040 [US3] Create `src/main/java/com/epam/aidial/deployment/manager/web/validation/ForbiddenPredictorArgsValidator.java` — `ConstraintValidator<ForbiddenPredictorArgs, Object>` that reflects `command` and `args` lists on the request DTO (use Spring `BeanWrapper` or direct casting to the known DTO type) and rejects per research.md §R-007: any token `--return_probabilities` (exact match), any `--task=<value>` or pair `["--task", "<value>"]` where value ≠ `sequence_classification`. Build the violating message naming the offending arg.
- [ ] T041 [US3] Annotate `@ForbiddenPredictorArgs` on `CreateInferenceDeploymentRequestDto` (in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/`) and the update analogue DTO. Verify exact class names via `Glob` first.

### Tests for US3

- [ ] T042 [P] [US3] Unit tests in `InferenceTaskDetectorTest` for every rejection case: missing id2label, empty id2label, sparse `{0,2}`, non-integer keys, empty values, all-stub `{0:"LABEL_0",1:"LABEL_1"}`, partial stub (one stub + one real), HF 404, HF 503. Mock `HuggingFaceClient`.
- [ ] T043 [P] [US3] Unit tests `src/test/java/com/epam/aidial/deployment/manager/web/validation/ForbiddenPredictorArgsValidatorTest.java` covering `--return_probabilities`, `--return_probabilities=true`, `--task=fill-mask`, `["--task","fill-mask"]`, multi-arg lists, allowed args (`--dtype=float16`).
- [ ] T044 [US3] Functional tests (H2): `src/test/java/com/epam/aidial/deployment/manager/functional/h2/InferenceDeploymentChainedRejectionFunctionalTest.java` — assert every Story-3 acceptance scenario returns the right status + canonical message. Use a fixture HF server returning controlled responses per scenario.

**Checkpoint**: US3 stands alone. Every silent-mislabel failure mode is now a synchronous 400.

---

## Phase 6: User Story 4 — Operator-controlled transformer image + resources via configuration (Priority: P1)

**Goal**: Image and resources for the transformer container are driven entirely by application properties / env vars. Unset image at deploy time → fast, clear failure with no cluster mutation.

**Independent Test**: With `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` set to a value, deploy a chained deployment and inspect the manifest's transformer image. Override CPU/memory env vars; redeploy; inspect new resource values. Unset the image; redeploy; receive a clear error before any cluster call.

- [ ] T045 [US4] In `TextClassificationTransformerSection.apply(...)` (T022), read image from `props.getImage()` and the four resource fields from `props.getResources()`. Set them on the transformer container.
- [ ] T046 [US4] In `InferenceDeploymentManager.prepareServiceSpec(...)` (or wherever the chained-path entry happens), when `deployment.getDetectedTask() == TEXT_CLASSIFICATION` AND `transformerProperties.getImage()` is blank (`StringUtils.isBlank`), throw a typed `MissingTransformerImageException` BEFORE any KServe-client call. The exception maps to HTTP 503 (or 500 per project convention) via `DefaultExceptionHandler`.
- [ ] T047 [P] [US4] Create the exception class `src/main/java/com/epam/aidial/deployment/manager/service/deployment/MissingTransformerImageException.java`. Add a mapping in `DefaultExceptionHandler`.
- [ ] T048 [P] [US4] Unit-test extensions in `InferenceManifestGeneratorTest`: image flows through from properties; each of the four resource properties flows through; defaults (when the test injects a properties instance with defaults) match `100m`/`500m`/`256Mi`/`512Mi`.
- [ ] T049 [US4] Functional test (H2): assert that with the image property unset, the deploy endpoint returns the configured-failure HTTP code with the canonical message; no KServe `InferenceService` was created.

**Checkpoint**: US4 stands alone. Image and resources are operator-controlled. Failure modes are loud.

---

## Phase 7: User Story 5 — Updating the deployment's model triggers re-detection (Priority: P2)

**Goal**: Updating `modelName` on an existing inference deployment re-runs detection and updates the persisted fields. Topology can transition chain ↔ predictor-only across updates.

**Independent Test**: Update a chained deployment's `modelName` to a non-classification model; `detectedTask` becomes `NONE`; redeploy produces a predictor-only manifest. Reverse: update a predictor-only deployment to a text-classification model; chained manifest emitted on next deploy.

- [ ] T050 [US5] In the update flow (find the inference-update service method via `Glob` — likely `InferenceDeploymentService.update(...)` or analogous), detect whether the request's `source.modelName` differs from the persisted value. If yes, call `InferenceTaskDetector.detect(...)` with the new source and update the deployment's `detectedTask`/`detectedId2Label` before persisting. If unchanged, leave the existing values intact.
- [ ] T051 [P] [US5] Functional test (H2): chain → predictor-only transition. Create chained, update model to non-classification, assert detected fields cleared; redeploy and inspect manifest.
- [ ] T052 [P] [US5] Functional test (H2): predictor-only → chain transition. Create predictor-only, update to text-classification, assert detected fields populated; redeploy and inspect manifest.
- [ ] T053 [P] [US5] Functional test (H2): update with unusable new model → 400 (per US3 rules); persisted deployment state untouched.

**Checkpoint**: US5 stands alone. Model swaps are now safe.

---

## Phase 8: User Story 6 — Export / import preserves detected fields (Priority: P3)

**Goal**: Round-trip the new fields through export/import. Imported entries are validated against the same structural rules as REST inputs.

**Independent Test**: Export a chained deployment, delete it, import the archive — restored row has identical `detectedTask` and `detectedId2Label`. Hand-edit the archive to corrupt `detectedId2Label` (sparse keys); import — entry rejected per existing import-validation rules.

- [ ] T054 [US6] Add `detectedTask` and `detectedId2Label` fields to the export/import archive shape for inference deployments. Find the archive DTO/serializer via `Grep` for `export` / `import` under `src/main/java/com/epam/aidial/deployment/manager/service/config/` (per service-layer CLAUDE.md). Update the round-trip path domain ↔ archive.
- [ ] T055 [US6] Add import-time validation: when importing an inference deployment, apply the same `id2label` structural checks defined in T036 (extract them into a reusable validator method called from both the detector and the import flow). On violation, the import skips/rejects the entry per the existing `010-import-validations` pattern.
- [ ] T056 [US6] Functional test (H2) for the export/import round-trip; functional test for the malformed-archive rejection.

**Checkpoint**: US6 stands alone. Configuration portability covers the new fields.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, capability-spec updates, schema regeneration, OpenAPI annotations, final gate.

- [ ] T057 [P] Update `specs/inference-deployments/spec.md` per CLAUDE.md "Spec maintenance" rule: add Requirements / Scenarios for the new auto-detect behavior, mark them `Implemented via 021-inference-task-transformer`. Add `detectedTask` / `detectedId2Label` to the Key Entities and Implementation Notes sections. Document the lazy-migration behavior for legacy rows.
- [ ] T058 [P] Update `specs/kubernetes-manifests/spec.md` to describe the conditional `transformer` block emission, predictor `protocolVersion: v2` for chained deployments, and the auto-injected predictor args. Cross-reference 021.
- [ ] T059 [P] Update SpringDoc annotations on inference-deployment controller methods (`@RestController` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/`) per contracts/inference-deployment-api.md: new `@ApiResponse(responseCode = "400", ...)` and `@ApiResponse(responseCode = "503", ...)` entries; updated `@Schema` annotations on the new DTO fields.
- [ ] T060 Run `./gradlew generateDbSchema` and commit the regenerated `docs/db-schema.md` per the constitution's "Schema documentation" rule. Verify the diff matches the V1.59 migration.
- [ ] T061 Run `./gradlew checkstyleMain checkstyleTest` — fix any style issues (180-char lines, Commons `StringUtils`/`CollectionUtils` usage, no wildcard imports).
- [ ] T062 Run `./gradlew clean build` — full PR gate including all testcontainer functional tests. All tests pass.
- [ ] T063 Update `specs/README.md` Recent-features table row for 021 — Status flips to `Implemented` (only after T062 passes; this is the gate). Capability column already set in spec.md.

---

## Dependency Graph

```text
Phase 1 (Setup, T001)
    │
    ▼
Phase 2 (Foundational, T002-T008)
    │   T002, T003, T004 — migrations (3 vendors, [P])
    │   T005 — InferenceTask enum (independent, [P])
    │   T006 — TextClassificationTransformerProperties (independent, [P])
    │   T007 — application.yml defaults (depends on T006)
    │   T008 — docs/configuration.md (independent, [P])
    │
    ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │ All P1 stories may proceed in parallel after Phase 2 completes:    │
   │                                                                    │
   │   Phase 3 (US1 — MVP)  ─── T009-T030                               │
   │   Phase 4 (US2)        ─── T031-T034                               │
   │   Phase 5 (US3)        ─── T035-T044                               │
   │   Phase 6 (US4)        ─── T045-T049                               │
   │                                                                    │
   │ US2 depends on T014 (detector) — coordinate with US1               │
   │ US3 depends on T014 (detector) — coordinate with US1               │
   │ US4 depends on T022/T023 (manifest gen) — coordinate with US1      │
   └────────────────────────────────────────────────────────────────────┘
    │
    ▼
   Phase 7 (US5, T050-T053) — depends on US1, US2, US3 (uses detector + update flow + rejection paths)
    │
    ▼
   Phase 8 (US6, T054-T056) — depends on US1 (new fields exist) + US3 (validator reuse)
    │
    ▼
   Phase 9 (Polish, T057-T063) — final gate
```

**Cross-story coordination notes**:

- US2 and US3 both extend `InferenceTaskDetector`. Land US1's T014 first (the happy-path detector), then US2 and US3 can land in any order on top.
- US4 extends `InferenceManifestGenerator` and `InferenceDeploymentManager` — these are also touched by US1. Land US1's T023/T024 first, then US4 layers properties-driven configuration on top.
- The "lazy migration" hook in US2 (T032) and the "missing image" gate in US4 (T046) both modify `prepareServiceSpec` — sequence them carefully or fold both into a single PR.

## Parallel execution examples

After Phase 2 completes, a single developer can implement US1 (T009 → T030) sequentially. Two-developer team can split: developer A on US1 + US4, developer B on US2 + US3, joining for US5/US6/Polish.

Tasks marked `[P]` within a phase can be opened as parallel pull requests:

- **Phase 2**: T002, T003, T004, T005, T006, T008 all `[P]` — six trivially independent PRs (migrations are file-disjoint, enum/props are isolated).
- **Phase 3**: T011 (ModelConfig DTO), T013 (DetectionResult record), T015 (domain field), T016 (entity column), T018 (DTO field), T021 (mapping constants), T027, T028, T030 — all `[P]` within US1 once T009/T010/T012/T014 land.
- **Phase 5**: T035 (exception hierarchy), T039 (annotation), T042, T043 — all `[P]`.

## Implementation Strategy

1. **MVP**: complete Phase 1 + Phase 2 + Phase 3 (US1) and stop. The product is shippable — text-classification deployments work end-to-end, non-classification deployments use the existing predictor-only code path (not yet "officially" auto-detected, but functionally unchanged from today).
2. **Backward-compat hardening**: add Phase 4 (US2) — explicit non-classification path + lazy migration for legacy rows.
3. **Hardening**: add Phase 5 (US3) — boundary validation, forbidden-args, friendly error mapping.
4. **Operational tuning**: add Phase 6 (US4) — properties-driven image + resources, explicit deploy-time gate.
5. **Iteration support**: add Phase 7 (US5) — model swaps trigger re-detection.
6. **Portability**: add Phase 8 (US6) — export/import round-trip.
7. **Final polish**: Phase 9 — spec updates, schema doc regen, OpenAPI, PR gate.
