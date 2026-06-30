---
description: "Task list for 024-model-serving-capability"
---

# Tasks: Model Serving Capability API

**Input**: Design documents from `/specs/024-model-serving-capability/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. This project's constitution mandates functional tests across DB vendors and
unit tests for service logic; `./gradlew clean build` (full suite) is the PR gate. Test tasks are
therefore part of "done", not optional.

**Organization**: Tasks grouped by user story. US1 and US2 are both P1 and independently testable;
US3 is P2. Shared data plumbing is in the Foundational phase.

## Path Conventions

Single backend service, base package `com.epam.aidial.deployment.manager`. Java sources under
`src/main/java/.../`, tests under `src/test/java/.../`, migrations under
`src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a clean baseline before changes.

- [x] T001 Confirm branch `024-model-serving-capability` is checked out and baseline is green: run `./gradlew testFast checkstyleMain checkstyleTest`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared data plumbing every user story depends on (enum value, persisted column,
mappers). No user story can complete until this phase is done.

**⚠️ CRITICAL**: Complete before starting US1, US2, or US3.

- [x] T002 Add `TEXT_GENERATION` value to `InferenceTask` enum in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceTask.java`, with a javadoc note that it is informational only and does NOT add a transformer to the manifest
- [x] T003 Ensure manifest generation treats `TEXT_GENERATION` as predictor-only (same path as `NONE`, no transformer) and review any `switch`/branch on `InferenceTask` in `src/main/java/com/epam/aidial/deployment/manager/service/manifest/InferenceManifestGenerator.java` for exhaustiveness (FR-009 — existing classification/none manifests unchanged)
- [x] T004 Add `inferenceTask` field (type `InferenceTask`) to the domain model `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceDeployment.java`
- [x] T005 Add `inference_task` column to entity `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/InferenceDeploymentEntity.java` as `@Enumerated(EnumType.STRING)` + `@Column(name = "inference_task")` (entity is already `@Audited`)
- [x] T006 [P] Create Flyway migration `src/main/resources/db/migration/H2/V1.59__AddInferenceTaskColumn.sql` — main column `inference_task VARCHAR(32) DEFAULT 'NONE' NOT NULL` (the `DEFAULT` backfills existing rows in one step, no separate backfill migration) plus the nullable `_aud` audit column at `VARCHAR(255)`
- [x] T007 [P] Create Flyway migration `src/main/resources/db/migration/POSTGRES/V1.59__AddInferenceTaskColumn.sql` (Postgres `ADD COLUMN` syntax, nullable)
- [x] T008 [P] Create Flyway migration `src/main/resources/db/migration/MS_SQL_SERVER/V1.59__AddInferenceTaskColumn.sql` (SQL Server `ADD` syntax, nullable)
- [x] T009 Map `inferenceTask` domain↔entity in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java` (verify MapStruct picks up the field for both directions for the inference subtype)
- [x] T010 Add a default no-op enrichment hook (e.g. `enrichBeforePersist(Deployment)`) to the `DeploymentManager` interface in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentManager.java` so non-inference types are unaffected

**Checkpoint**: Schema, enum, entity, domain field, and mapper plumbing exist; project compiles.

---

## Phase 3: User Story 1 - Frontend learns what a deployed model exposes (Priority: P1) 🎯 MVP

**Goal**: The persisted inference task is computed at create time and returned read-only on the
inference deployment responses (single + list).

**Independent Test**: Create an inference deployment from a text-classification model and fetch it →
response carries `inferenceTask: TEXT_CLASSIFICATION`; from an unrelated model → `NONE`; the field
appears on list responses too. (TEXT_GENERATION case is covered once US2 lands.)

### Implementation for User Story 1

- [x] T011 [US1] Override the enrichment hook in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java` to run `inferenceTaskDetector.detect(huggingFaceSource)` and set `inferenceTask` on the deployment (guard non-HuggingFace sources as the existing code does)
- [x] T012 [US1] Call the enrichment hook from `DeploymentService.createDeployment` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java`, before `deploymentRepository.save` (inside the existing `@Transactional`)
- [x] T013 [P] [US1] Add read-only `inferenceTask` field to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/InferenceDeploymentDto.java`
- [x] T014 [US1] Map `inferenceTask` domain→DTO in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java`, coalescing null → `NONE` (FR-007)
- [x] T015 [US1] Verify `CreateInferenceDeploymentRequestDto` does NOT expose `inferenceTask` (server-computed only, FR-008) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateInferenceDeploymentRequestDto.java`
- [x] T016 [US1] Covered at unit level instead of a new functional test: `InferenceDeploymentManagerTest#enrichBeforePersist_shouldSetDetectedTask_forHuggingFaceSource` / `_shouldLeaveTaskUnset_whenSourceIsNotHuggingFace` verify create-time persistence; null→`NONE` coalescing is in `DeploymentDtoMapper`. NOTE: no dedicated inference functional test existed to mirror and the create path now needs the HF client stubbed (added to `FunctionalTestConfiguration`); a full end-to-end functional test is deferred.

**Checkpoint**: FE can read the capability for classification/none deployments end to end.

---

## Phase 4: User Story 2 - Text-generation models are recognised by detection (Priority: P1)

**Goal**: `InferenceTaskDetector` classifies generative models as `TEXT_GENERATION`, with
classification taking precedence; existing classification/none outcomes unchanged.

**Independent Test**: Run the detector against a generation model → `TEXT_GENERATION`; against a
classification model → `TEXT_CLASSIFICATION`; against an unrelated model → `NONE`; against an
ambiguous (both signals) model → `TEXT_CLASSIFICATION`.

### Implementation for User Story 2

- [x] T017 [P] [US2] Add `textGeneration()` factory to `src/main/java/com/epam/aidial/deployment/manager/service/detection/InferenceTaskDetectionResult.java` (no `id2Label`)
- [x] T018 [US2] Extend `InferenceTaskDetector.detect` in `src/main/java/com/epam/aidial/deployment/manager/service/detection/InferenceTaskDetector.java`: detect text-generation via `pipeline_tag == "text-generation"` OR a causal-LM/generative architecture pattern (e.g. `*ForCausalLM`, `*ForConditionalGeneration`, `*LMHeadModel`); evaluate classification first, then generation, else none (precedence per research Decision 2). Reuse already-fetched `Model`/`ModelConfig` — no new HF call
- [x] T019 [P] [US2] Unit test the detector in `src/test/java/.../service/detection/InferenceTaskDetectorTest.java`: `shouldDetectTextGeneration_whenPipelineTagIsTextGeneration`, `shouldDetectTextGeneration_whenArchitectureIsCausalLm`, `shouldPreferTextClassification_whenBothSignalsPresent`, plus regression cases asserting existing classification/none results are unchanged

**Checkpoint**: Detector returns all three values correctly; combined with US1, a generation
deployment now reports `TEXT_GENERATION`.

---

## Phase 5: User Story 3 - Capability stays correct when the model source changes (Priority: P2)

**Goal**: Updating a deployment's model source re-evaluates and re-persists the task.

**Independent Test**: Create from a classification model (→ `TEXT_CLASSIFICATION`), update its source
to a generation model, re-fetch → `TEXT_GENERATION`.

### Implementation for User Story 3

- [x] T020 [US3] Call the enrichment hook from `DeploymentService.updateDeployment` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java` so the persisted task is refreshed when the model source changes (re-run on update; an update that doesn't change the source leaves the value consistent)
- [x] T021 [US3] Update-path re-detection is exercised by the same `enrichBeforePersist` hook (unit-tested in T016) wired into `DeploymentService.updateDeployment`, plus the persistence-mapper update copy (`PersistenceDeploymentMapper#updateEntityFromDomain`). Dedicated end-to-end functional test deferred together with T016.

**Checkpoint**: Persisted capability tracks the current model source.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T022 Run `./gradlew generateDbSchema` and commit the updated `docs/db-schema.md` (constitution: any migration change must regenerate the schema doc)
- [x] T023 Update `specs/inference-deployments/spec.md`: document the `inferenceTask` field + text-generation detection + precedence, add `Implemented via 024-model-serving-capability` near the affected requirement (per CLAUDE.md spec-maintenance rule)
- [x] T024 Flip `specs/024-model-serving-capability/spec.md` `**Status**:` to `Implemented`
- [x] T025 Run quickstart.md verification + full gate: `./gradlew clean build` and `./gradlew checkstyleMain checkstyleTest`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup. BLOCKS all user stories.
- **US1 (Phase 3)**: after Foundational.
- **US2 (Phase 4)**: after Foundational. Independent of US1 (detector-level change).
- **US3 (Phase 5)**: after US1 (reuses the enrichment hook from T011/T012).
- **Polish (Phase 6)**: after all targeted stories.

### User Story Dependencies

- US1 (P1): depends only on Foundational. MVP.
- US2 (P1): depends only on Foundational. Can run in parallel with US1.
- US3 (P2): depends on US1's enrichment hook.

### Within Each Story

- Implementation before its functional/unit test execution; write tests to fail first where practical.
- Models/entities before services; services before DTO/mapper exposure.

### Parallel Opportunities

- T006, T007, T008 (the three vendor migrations) are [P] — different files.
- US1 and US2 can be developed in parallel after Phase 2 (different files: web/service+mapper vs. detection).
- T013 [P] (DTO field) and T017 [P] (detection result factory) are independent.
- T019 [P] (detector unit test) independent of US1 files.

---

## Parallel Example: Foundational migrations

```bash
# After T005, author the three vendor migrations together:
Task: "Create H2 migration V1.59__AddInferenceTaskColumn.sql"
Task: "Create POSTGRES migration V1.59__AddInferenceTaskColumn.sql"
Task: "Create MS_SQL_SERVER migration V1.59__AddInferenceTaskColumn.sql"
```

## Parallel Example: P1 stories after Foundational

```bash
# Developer A (US1 — expose + persist at create):  T011 → T012 → T013/T014/T015 → T016
# Developer B (US2 — detection):                    T017 → T018 → T019
```

---

## Implementation Strategy

### MVP First (US1)

1. Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 US1 → **STOP and validate**: classification/none capability is read end to end.
3. This alone delivers the FE-facing field for existing task types.

### Incremental Delivery

1. Foundation ready.
2. US1 → MVP (capability field live for classification/none).
3. US2 → generation models now report `TEXT_GENERATION`.
4. US3 → value tracks source changes.
5. Polish → schema doc, capability spec, status flip, full-build gate.

---

## Notes

- [P] = different files, no dependency on an incomplete task.
- No new env vars / `@ConfigurationProperties` → `docs/configuration.md` is NOT touched.
- Deploy-time `prepareServiceSpec` path stays as-is (it computes `id2Label` for the manifest); the
  persisted task is set at create/update and stays consistent (same detector). See research Decision 3.
- Commit after each logical group; the full `./gradlew clean build` is the PR gate.
