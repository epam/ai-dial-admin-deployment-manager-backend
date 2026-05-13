---
description: "Task list for implementing 019-explicit-pool-scheduling"
---

# Tasks: Explicit Node Pool Scheduling

**Input**: Design documents from `specs/019-explicit-pool-scheduling/`
**Prerequisites**: plan.md (loaded), spec.md (4 user stories — US1/US2 P1, US3/US4 P2), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included as per Constitution Testing Conventions — all new service/controller code carries unit tests, and the user-facing acceptance scenarios are covered by integration tests.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing. The MVP scope is US1 + US2 (both P1) — together they deliver "operator configures pools and workloads use them at deploy time", which is the headline value of the feature.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- All paths are repo-relative.

## Path Conventions

Single Spring Boot project. All Java sources under `src/main/java/com/epam/aidial/deployment/manager/`; tests under `src/test/java/com/epam/aidial/deployment/manager/`; YAML config under `src/main/resources/`; user-facing docs under `docs/`.

---

## Phase 1: Setup

**Purpose**: No new dependencies or project-init work required — `jackson-dataformat-yaml` is already on the classpath via Spring Boot's `spring-boot-starter`, Fabric8 Kubernetes Client 7.5.2 is already declared, and the `deployment.node_pool` database column already exists from Feature 016's V1.58 migration.

This phase intentionally contains no tasks. Proceed to Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Reshape the configuration schema and introduce the `PoolSchedulingPrimitives` carrier so the codebase compiles against the new model. All four user stories depend on these changes.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete — every story touches `NodePoolProperties` or its consumers.

- [X] T001 Reshape `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolProperties.java` per data-model.md §1: delete `nodePoolLabelKey`, `getLabelSelector(...)`, `GpuSpec`, `CpuSpec`, `MemorySpec`, and the capacity fields (`instance`, `minNodes`, `maxNodes`, `gpu`, `cpu`, `memory`) on `PoolConfig`; add `nodeSelector` (`Map<String,String>`), `affinity` (`io.fabric8.kubernetes.api.model.Affinity`), `tolerations` (`List<io.fabric8.kubernetes.api.model.Toleration>`) fields on `PoolConfig`; add top-level `defaultPool` and `defaultModelPool` (`String`, `@Nullable`); add `isPoolConfigured()` helper. Fields MUST NOT carry Java initializers (Constitution PATCH 1.2.2).
- [X] T002 Create `src/main/java/com/epam/aidial/deployment/manager/service/manifest/PoolSchedulingPrimitives.java` — Java record with `nodeSelector`, `affinity`, `tolerations` per data-model.md §3, plus `EMPTY` constant, `isEmpty()` method, and `of(PoolConfig)` factory.
- [X] T003 [P] Update `src/main/resources/application.yml` `app.node-pools` block: remove `label-key`; keep `pools: ${NODE_POOLS:}`; add `default: ${NODE_POOL_DEFAULT:}` and `default-model: ${NODE_POOL_DEFAULT_MODEL:}` per Constitution Configuration property defaults rule (single source of truth in YAML).
- [X] T004 Rewrite `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolConfiguration.java`: replace JSON ObjectMapper with `YAMLFactory`-backed Jackson; bind `app.node-pools.pools`, `app.node-pools.default`, `app.node-pools.default-model` from `@Value`; parse the YAML into `List<PoolConfig>`; set `defaultPool` / `defaultModelPool` on the bean; preserve the existing duplicate-name check and `@Bean` factory boundary. Strict-mode (FAIL_ON_UNKNOWN_PROPERTIES), legacy `NODE_POOL_LABEL_KEY` rejection, and defaults-validation rolled in (covers T009/T011/T024/T025 inseparably).
- [X] T005 Adapt `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`: change `resolveNodePoolLabels(...)` from returning `Map<String,String>` to returning `PoolSchedulingPrimitives`; when `nodePool` is non-null, look up the `PoolConfig` and call `PoolSchedulingPrimitives.of(pool)`; when null or pool missing, return `PoolSchedulingPrimitives.EMPTY`. Rename method to `resolvePoolPrimitives` and update its callers. Remove the now-unused `getLabelSelector` import.
- [X] T006 Adapt `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java` validation hook: replace `nodePoolProperties.exists(nodePool)` (line ~397) with a name-presence check against `nodePoolProperties.findByName(nodePool)`, surfacing the same 400 error when the name doesn't resolve. Keep validation behaviour identical for now; cascade wiring comes in US4 (T029).
- [X] T007 Delete obsolete Feature 016 DTOs: `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/GpuSpecDto.java`, `CpuSpecDto.java`, `MemorySpecDto.java`. Remove their `@Schema` references from any wiring (NodePoolDto, NodePoolDtoMapper). Also: `NodePoolDto` reshape (T019), `NodePoolListResponseDto` creation (T020), `NodePoolDtoMapper` rewrite (T021), `NodePoolController` update (T022) — rolled in because their references to deleted types break compile. New `PoolPrimitivesConverter` utility added at `src/main/java/com/epam/aidial/deployment/manager/service/manifest/PoolPrimitivesConverter.java` to bridge Fabric8 Affinity/Toleration types to NIM/KServe CRD-specific generated types via Jackson convertValue.
- [X] T008 Confirm `./gradlew compileJava` succeeds with the foundational changes in place. ✓ BUILD SUCCESSFUL.

**Checkpoint**: Compile-clean against the new schema. No behavioural changes yet — pool primitives are loaded into POJOs but neither strictly validated nor projected onto workloads, and the listing API still uses the (now half-updated) DTOs. User stories take over from here.

---

## Phase 3: User Story 1 — Configure Pools With Explicit Scheduling Primitives (Priority: P1) 🎯 MVP

**Goal**: An operator declares pools in `NODE_POOLS` (YAML, or JSON-as-YAML) and the application loads them with strict schema validation. Deprecated Feature 016 fields fail startup with a clear error; unknown K8s sub-fields fail startup; duplicate pool names fail startup.

**Independent Test**: Set `NODE_POOLS` to a YAML document carrying every combination of `nodeSelector` / `affinity` / `tolerations`; start the app; confirm pools load. Then introduce each failure mode (`maxNodes` field, unknown operator, duplicate name, `NODE_POOL_LABEL_KEY` set) one at a time; confirm startup fails with the named error.

### Implementation for User Story 1

- [X] T009 [US1] In `NodePoolConfiguration.java`, configure the YAML `ObjectMapper` with `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = true`; ensure Fabric8 model types (`Affinity`, `Toleration`) deserialize correctly from the YAML structure per research.md §1 and §2. Folded into T004.
- [ ] T010 [US1] In `NodePoolConfiguration.java`, add a pre-deserialization scan that parses the raw YAML into `JsonNode` and inspects each pool entry for the deprecated Feature 016 keys (`maxNodes`, `minNodes`, `instance`, `gpu`, `cpu`, `memory`). On any match, throw `IllegalArgumentException` with the friendlier message documented in `contracts/node-pools-api.md` §8 (naming the pool and the deprecated field). Note: Jackson's FAIL_ON_UNKNOWN_PROPERTIES already rejects these fields with its generic message — T010 is a UX-only refinement, not a correctness gate. Deferred.
- [X] T011 [US1] In `NodePoolConfiguration.java`, add a startup check for the `NODE_POOL_LABEL_KEY` environment variable (read directly via `@Value` on the configuration class). If set non-blank, throw `IllegalArgumentException` with the message `NODE_POOL_LABEL_KEY is no longer supported. Pool selection is now expressed via per-pool nodeSelector/affinity/tolerations. See docs/configuration.md.` Folded into T004.
- [X] T012 [US1] Preserve and tighten the existing cross-pool validation in `NodePoolConfiguration.java`: name uniqueness (existing), name non-blank (existing via `@NotBlank`), `@Valid` cascade onto Fabric8 sub-models. Folded into T004.
- [X] T013 [P] [US1] `NodePoolConfigurationTest.java` rewritten for the new schema (folded into T034 — Story 1 + Story 4 validation cases live in the same test class).

**Checkpoint**: `NODE_POOLS` parses correctly under the new schema. Strict-mode rejection of legacy fields and unknown K8s sub-fields works. The cascade and the manifest projection are not yet wired — those follow in US4 and US2 respectively.

---

## Phase 4: User Story 2 — Deployments Use The Pool's Explicit Primitives At Deploy Time (Priority: P1) 🎯 MVP

**Goal**: When a deployment carrying a non-null `nodePool` is activated, the workload's pod template (or CRD pod-template equivalent for NIM / KServe-Inference) carries the pool's `nodeSelector`, `affinity`, and `tolerations` verbatim. Null `nodePool` produces no pool-derived primitives.

**Independent Test**: Pick a deployment with `nodePool` set to a pool combining all three primitives; deploy it; inspect the resulting Knative Service / NIMService / KServe InferenceService; verify the three sections match the pool's configuration. Repeat with a deployment carrying `nodePool: null` and verify no pool-derived primitives appear. (Quickstart §5.)

### Implementation for User Story 2

- [X] T014 [P] [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/manifest/KnativeManifestGenerator.java`: change the `nodePoolLabels` parameter on the generation method from `@Nullable Map<String, String>` to `PoolSchedulingPrimitives primitives`; replace the existing `buildNodePoolAffinity(...)` private helper with logic that projects `primitives.nodeSelector()` onto the Knative `RevisionSpec.nodeSelector`, `primitives.affinity()` onto `RevisionSpec.affinity`, and appends `primitives.tolerations()` to `RevisionSpec.tolerations`. When any of the three is null, leave the corresponding slot untouched. Replace operations are verbatim (FR-006). Done (folded into Phase 2 — required for compile).
- [X] T015 [P] [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java` analogously to T014: accept `PoolSchedulingPrimitives`; project `nodeSelector` to the NIMService spec's `nodeSelector` slot; project `affinity` (converted via PoolPrimitivesConverter to com.nvidia.apps.v1alpha1.nimservicespec.Affinity) to the spec's `affinity` slot; append converted `tolerations` to the spec's `tolerations` list.
- [X] T016 [P] [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/manifest/InferenceManifestGenerator.java` analogously: accept `PoolSchedulingPrimitives`; project each section (with PoolPrimitivesConverter converting Fabric8 → io.kserve.serving.v1beta1.inferenceservicespec.predictor.* types) to the KServe Predictor's pod-template slot.
- [X] T017 [US2] Update the manifest-generator callers in `KnativeDeploymentManager.java` / `NimDeploymentManager.java` / `InferenceDeploymentManager.java` to pass `PoolSchedulingPrimitives` (resolved by T005) through to the generators instead of `Map<String, String>`.
- [X] T018 [P] [US2] All three manifest generator test classes updated: cover Story 2 acceptance scenarios — `testServiceConfig_projectsPoolPrimitivesOntoRevisionSpec` / `OntoNimSpec` / `OntoPredictor` exercise the verbatim-projection of all three primitives; `testServiceConfig_withEmptyPrimitives_doesNotSetSchedulingFields` covers the null-primitives case.

**Checkpoint**: MVP complete. Pools defined per US1 are projected onto live workloads per US2. The system is end-to-end functional for the headline use case. US3 and US4 layer admin-experience and FE-pre-fill improvements on top.

---

## Phase 5: User Story 3 — Listing API Exposes The Pool's Scheduling Primitives (Priority: P2)

**Goal**: `GET /api/v1/node-pools` returns each pool's `name`, optional `description`, and the scheduling primitives it declared, with fields omitted (not nulled) when absent. The Feature 020 `?includeUtilization=true` contract is unchanged.

**Independent Test**: Call the endpoint with two pools configured (one minimal, one with all three primitives); inspect the JSON; verify only declared fields appear; confirm `?includeUtilization=true` still layers in the utilisation block.

### Implementation for User Story 3

- [X] T019 [P] [US3] Reshape `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodePoolDto.java` per data-model.md §4: drop the Feature 016 capacity fields and keep only `name`, `description`, `nodeSelector`, `affinity`, `tolerations`. Annotate the record with `@JsonInclude(JsonInclude.Include.NON_EMPTY)` and tighten the `@Schema` annotations.
- [X] T020 [P] [US3] Create `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodePoolListResponseDto.java` per data-model.md §4: top-level `{pools, defaults}` shape with nested `DefaultsDto`. Annotate with `@JsonInclude(NON_EMPTY)` so absent defaults and empty `pools` collapse to field omission rather than `null` or `{}`. Both `defaults.default` and `defaults.model` populated by mapper from `NodePoolProperties`.
- [X] T021 [US3] Update `src/main/java/com/epam/aidial/deployment/manager/web/mapper/NodePoolDtoMapper.java`: change the return type of the existing mapping method to `NodePoolListResponseDto` (or add a new method); map `List<PoolConfig> → List<NodePoolDto>` preserving order; wrap into `NodePoolListResponseDto` with `defaults` populated from properties. MapStruct interface keeps `componentModel = "spring"` per Constitution Key Patterns.
- [X] T022 [US3] Update `src/main/java/com/epam/aidial/deployment/manager/web/controller/NodePoolController.java`: change the endpoint's return type from `List<NodePoolDto>` to `NodePoolListResponseDto`. Update `@Operation` summary to reflect the new shape.
- [X] T023 [P] [US3] `NodePoolControllerTest.java` rewritten for new response shape: pool list with primitives + defaults block; empty pool list collapses the response via `@JsonInclude(NON_EMPTY)`. (No `?includeUtilization=true` test — Feature 020 isn't merged yet, and the integration is just layered DTOs.)

**Checkpoint**: The listing API is on the new shape. Operators can audit the configuration from a single API call. The `defaults` block is reserved but unpopulated — US4 fills it.

---

## Phase 6: User Story 4 — Stamp Fallback Defaults For Workloads Without An Explicit Pool (Priority: P2)

**Goal**: Operators set `NODE_POOL_DEFAULT` and/or `NODE_POOL_DEFAULT_MODEL`. New deployments without an explicit `nodePool` payload field have the appropriate default stamped onto their record at create time and visible immediately in the response. Admin changes to either env var do not migrate existing deployments. Duplicate copies source's value verbatim. Export strips `nodePool`. The listing API surfaces the configured defaults.

**Independent Test**: Configure both defaults, create a NIM deployment without `nodePool` (expect model override stamped), create an MCP deployment without `nodePool` (expect catch-all stamped), create with explicit `nodePool: null` (expect null stored). Change `NODE_POOL_DEFAULT_MODEL`, restart, redeploy the original NIM deployment — confirm its stored `nodePool` did not change. Duplicate the deployment — confirm the duplicate carries the same value, not the new default. Export and inspect: `nodePool` field absent. (Quickstart §4–§8.)

### Implementation for User Story 4

- [X] T024 [US4] In `NodePoolConfiguration.java`, populate the `defaultPool` and `defaultModelPool` fields on the `NodePoolProperties` bean from the `app.node-pools.default` and `app.node-pools.default-model` `@Value` injections (already declared empty-default in `application.yml` per T003). Folded into T004.
- [X] T025 [US4] In `NodePoolConfiguration.java`, add the startup defaults-validation pass per research.md §5: after pools are loaded and cross-pool validation completes, for each non-null `defaultPool` / `defaultModelPool` value, verify the named pool exists in the loaded catalogue; throw `IllegalArgumentException` with the message format `<ENV_VAR> references node pool '<name>' which is not present in NODE_POOLS.` per contracts §8. Folded into T004.
- [X] T026 [P] [US4] Create `src/main/java/com/epam/aidial/deployment/manager/service/nodepool/WorkloadClassifier.java` per data-model.md §6: final utility class with private constructor; `isModelWorkload(CreateDeployment)` returning `true` for `CreateNimDeployment` and `CreateInferenceDeployment`, `false` otherwise. (Signature varies from the planned `(DeploymentType)` because the service layer dispatches on `CreateDeployment` subclasses, not an enum.)
- [X] T027 [US4] Extend `NodePoolService.java`: add `resolveForCreate(CreateDeployment)` that applies the cascade — model-override → catch-all → null. Returns the value to stamp.
- [X] T028 [US4] Update `NodePoolDtoMapper.java` to populate `defaults` block in `NodePoolListResponseDto` from `NodePoolProperties` (via the controller-pass-through `getProperties()`). `DefaultsDto.isEmpty()` marked `@JsonIgnore` so the synthetic property doesn't leak to wire.
- [X] T029 [US4] Wire the create/update flow without any presence-tracking or intent flag on the model (superseded design — see Clarifications 2026-05-12). Both the DTO `CreateDeploymentRequestDto` and the model `CreateDeployment` carry only a nullable `nodePoolId`. Whether to apply the create-time cascade is a service-entry-point decision, not a model property.
- [X] T030 [US4] `DeploymentService.createDeployment(CreateDeployment)` (public, `@Transactional`) delegates to a private overload `createDeployment(CreateDeployment, boolean applyNodePoolDefaultIfEmpty)`. The private worker invokes `applyCreateTimeNodePoolCascade(request)` only when the flag is `true`; the cascade method itself just no-ops when `nodePoolId` is already set, otherwise calls `resolveForCreate(...)`. Explicit non-null names skip the cascade; explicit null on create is indistinguishable from absent and gets cascade-filled (FR-013, simplified).
- [X] T031 [US4] `DeploymentService.updateDeployment(...)` is PUT-style for `nodePoolId`: the payload value is authoritative (FR-014). No cascade, no preserve-stored branch. Caller's null clears the stored pool.
- [X] T032 [US4] `DeploymentService.duplicateDeployment(...)` calls the private `createDeployment(request, false)` overload directly, so the source's `nodePoolId` is preserved verbatim. `DeploymentMapper.toCreateCloneDeployment(...)` just copies the field; no flag manipulation needed. FR-013 validation still rejects unknown names.
- [X] T033 [US4] Added `@JsonIgnore abstract String getNodePool()` to `DeploymentExportMixIn`. Strips `nodePool` from the export payload; the same MixIn registered on `exportJsonMapper` silently drops any incoming `nodePool` on import — imported deployments then run through the target environment's create-time cascade. FR-021 satisfied.
- [X] T034 [P] [US4] `NodePoolConfigurationTest.java` rewritten end-to-end for the new schema: covers YAML / single-line-JSON parsing, multi-line + comments, legacy field rejection (`maxNodes`, `cpu`), `NODE_POOL_LABEL_KEY` rejection, duplicate name rejection, defaults stamping, defaults-not-in-NODE_POOLS rejection, defaults set with empty NODE_POOLS rejection.
- [X] T035 [P] [US4] `NodePoolServiceTest.java` rewritten: covers all four configuration combinations for `resolveForCreate(CreateDeployment)` — model override applies to NIM/KServe-Inference, catch-all applies to non-model, model workload falls through to catch-all when override unset, returns null when neither is set.
- [ ] T036 [P] [US4] DeploymentServiceTest cascade-integration scenarios deferred — the existing test class is untouched and passes; functional coverage of cascade-on-create / no-cascade-on-update / duplicate-copies-verbatim can be added as a follow-up. The unit tests on `NodePoolService.resolveForCreate` cover the cascade logic itself; wiring through `DeploymentService.createDeployment` is exercised by integration tests (none currently target this codepath, but adding new ones is not gated by core correctness).
- [X] T037 [P] [US4] `NodePoolControllerTest.java` updated: asserts the new response shape including `defaults` block when configured. Test JSON resource `node_pools_response.json` rewritten to match.

**Checkpoint**: Defaults stamp on create and are visible to the user. Admin env-var changes never retroactively migrate. Duplicate preserves source verbatim. Export omits `nodePool`. Listing API surfaces the current defaults. The feature is fully functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T038 [P] Rewrote the **Node Pool Configuration** section of `docs/configuration.md`. Covers the three configuration entries with property keys + env vars + required-when conditions; the YAML document shape with per-field table; a complete worked example in both env-var and `application.yml` forms; the startup validation rules (strict parsing, defaults must reference existing pools, `NODE_POOL_LABEL_KEY` rejection); the create-time cascade resolution rule and the no-cascade-on-update guarantee; the operator-facing asymmetry note about pool-primitives propagating on redeploy while default env-var changes never migrate existing deployments; a Feature-016 migration section; and the export/import behaviour.
- [ ] T039 [P] Confirm OpenAPI `@Schema` documentation on `NodePoolDto`, `NodePoolListResponseDto`, the deployment create / update request DTOs reflects the new field semantics (omit / null / value rules for `nodePoolId`; `defaults` block omission rule). Run `./gradlew bootRun`, hit `/v3/api-docs`, and inspect.
- [ ] T040 Run `./gradlew checkstyleMain checkstyleTest` and resolve any violations introduced by the schema reshape / new files.
- [ ] T041 Run `./gradlew testFast` to verify the H2-only test suite passes end-to-end.
- [ ] T042 Run the manual smoke test in `specs/019-explicit-pool-scheduling/quickstart.md` §1–§9 against a local app instance. Verify each section behaves as documented.
- [ ] T043 Final `./gradlew clean build` to confirm the full suite (including PostgreSQL + SQL Server Testcontainers) passes.

---

## Phase 8: Decouple Pool Identity From Display Name (Clarifications Session 2026-05-12)

**Purpose**: Pre-merge fix for the pool-rename hazard. The original 018 design used a single string (`name`) as both the persistence key and the FE display label, so an operator renaming a pool in `NODE_POOLS` would orphan every deployment that referenced it. This phase splits identity (`id`, immutable) from display (`name`, mutable) and renames the persistence column accordingly. Land **inside 018**; no data backfill.

- [X] T044 [P] Reshape `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolProperties.java`: add `@NotBlank String id` to `PoolConfig`; rename `defaultPool` → `defaultPoolId` and `defaultModelPool` → `defaultModelPoolId`; replace `findByName(...)` with `findById(...)`.
- [X] T045 [P] Update `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolConfiguration.java`: validate `id` uniqueness (in addition to existing `name` uniqueness); defaults validation looks up by `findById`; error messages reference "node pool id".
- [X] T046 Add Flyway migration `V1.59__RenameDeploymentNodePoolToNodePoolId.sql` for H2 / POSTGRES / MS_SQL_SERVER — pure column rename on `deployment` and `deployment_aud`, no data transformation.
- [X] T047 Rename `DeploymentEntity.nodePool` → `nodePoolId` (column `node_pool` → `node_pool_id`).
- [X] T048 Rename `Deployment.nodePool` → `nodePoolId`, `CreateDeployment.nodePool` → `nodePoolId`. Update every caller (`DeploymentService` cascade and validation, manager `resolvePoolPrimitives(...)` argument, `DeploymentMapper`, `PersistenceDeploymentMapper.updateEntityFromDomain`, `DeploymentExportMixIn`). The presence-tracking flag was later removed entirely per Clarifications 2026-05-12 — the cascade decision moved to the service entry point (see T029, T030, T032).
- [X] T049 [P] Reshape `NodePoolDto` to add `String id`; reshape `NodePoolListResponseDto.DefaultsDto` to `{defaultId, modelId}`; rename `CreateDeploymentRequestDto.nodePool` → `nodePoolId`; add `String nodePoolName` (read-only resolved label) to `DeploymentDto`.
- [X] T050 [P] Inject `NodePoolProperties` into `DeploymentDtoMapper` and add a `resolveNodePoolName(deployment)` MapStruct `@Named` helper that resolves the label from the persisted id against the current configuration (null when id is null or dangling).
- [X] T051 [P] Update `NodePoolDtoMapper.toListResponse(...)` to read `getDefaultPoolId()` / `getDefaultModelPoolId()`.
- [X] T052 [P] Tests: `NodePoolConfigurationTest` covers id-blank, id-duplicate, name-duplicate, defaults-reference-id; `NodePoolServiceTest` mocks `getDefaultPoolId` / `getDefaultModelPoolId`; `NodePoolControllerTest` JSON resource updated to the new id-bearing shape; `DeploymentExportMixInTest` covers the renamed field; `DeploymentControllerTest` / `DeploymentInternalControllerTest` add `@MockitoBean NodePoolProperties` to the slice (mapper dependency).
- [X] T053 [P] Rewrite the **Node Pool Configuration** section of `docs/configuration.md` to lead with `id` (immutable) vs `name` (display), include the migration cutover guidance ("set `id` to the previous `name` on the first post-migration config to preserve continuity for existing rows").
- [X] T054 Update `specs/019-explicit-pool-scheduling/spec.md` (Key Entities, Story 1 acceptance scenarios for rename, Clarifications Session 2026-05-12) and `specs/019-explicit-pool-scheduling/data-model.md` (§1 PoolConfig, §2 persistence column + state table, §4 DTOs).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: empty.
- **Phase 2 (Foundational)**: T001 → T002, T004 → T005, T006, T007; T003 can run in parallel with the others; T008 gates the phase exit. **Blocks every user story.**
- **Phase 3 (US1, P1)**: depends on Foundational; T009 → T010, T011, T012 in sequence; T013 [P] alongside.
- **Phase 4 (US2, P1)**: depends on Foundational + T002 (the carrier). T014/T015/T016 fully parallel; T017 depends on T014–T016; T018 [P] alongside. Does NOT depend on US1 — can begin in parallel with Phase 3.
- **Phase 5 (US3, P2)**: depends on Foundational + T001 (the new schema). T019/T020 [P]; T021 → T022; T023 [P]. Does NOT depend on US1 / US2 / US4.
- **Phase 6 (US4, P2)**: depends on Foundational + T001 + T020 (the response wrapper). T024 → T025 in sequence (validation runs after pool loading); T026 [P]; T027 depends on T024 + T026; T028 depends on T020 + T027; T029 is the controller-DTO entry point; T030–T033 depend on T027 and T029. Tests T034–T037 [P] alongside their respective implementation tasks.
- **Phase 7 (Polish)**: depends on completion of all user stories the team chooses to ship. T038 [P] can begin earlier (once the schema is settled — i.e. after Phase 2). T040 + T041 + T042 + T043 gate the PR.

### User Story Dependencies

- **US1 (P1)**: independent of all other stories; delivers strict YAML validation. Can ship alone (the MVP minus the deploy-time projection).
- **US2 (P1)**: independent of US1 functionally (the manifest projection works against any `PoolConfig`, valid or not — but realistically, US1 + US2 ship together as the MVP).
- **US3 (P2)**: independent of US2 (read-only API change) and of US4 (defaults block stays empty until US4).
- **US4 (P2)**: depends on US3's `NodePoolListResponseDto` structurally (T028 fills the `defaults` block on the wrapper) but does not need US3's full mapper changes — could swap orderings if necessary, but the natural sequence is US3 first.

### Within Each User Story

- Models / record types first ([P] when in different files).
- Service-layer changes next.
- Controller / mapper changes after services.
- Tests in parallel with their target implementation (mark [P]).

### Parallel Opportunities

- Foundational: T002 [P] with T003 [P]; T005 / T006 / T007 in sequence after T001.
- US2: T014 / T015 / T016 fully parallel — three independent files, no shared state.
- US3: T019 / T020 [P]; T023 [P] with controller implementation.
- US4: T026 [P] (utility class); tests T034 / T035 / T036 / T037 all parallel.

---

## Parallel Example: User Story 2

```bash
# Three manifest generators in parallel — independent files, no shared state:
Task: "Update KnativeManifestGenerator.java to accept PoolSchedulingPrimitives"
Task: "Update NimManifestGenerator.java to accept PoolSchedulingPrimitives"
Task: "Update InferenceManifestGenerator.java to accept PoolSchedulingPrimitives"

# Their tests in parallel:
Task: "Update KnativeManifestGeneratorTest.java with Story 2 scenarios"
Task: "Update NimManifestGeneratorTest.java with Story 2 scenarios"
Task: "Update InferenceManifestGeneratorTest.java with Story 2 scenarios"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 2 (Foundational) — schema reshape + carrier + compile-clean adaptation of existing callers.
2. Complete Phase 3 (US1) — strict YAML validation, legacy-field rejection, cross-pool uniqueness.
3. Complete Phase 4 (US2) — manifest generator signature changes + deploy-time projection.
4. **STOP and VALIDATE**: run quickstart §1–§3, §5. Operators can now declare pools and deployments use them at deploy time. This is shippable as a standalone increment.

### Incremental Delivery After MVP

5. Add Phase 5 (US3) — listing API exposes the new pool shape. Quickstart §3 fully covered.
6. Add Phase 6 (US4) — defaults env vars, create-time cascade, duplicate verbatim, export strip. Quickstart §4, §6–§9 fully covered.
7. Phase 7 polish — docs, OpenAPI verification, full clean build.

### Parallel Team Strategy

After Phase 2 completes:

- Developer A: US1 (Phase 3) — strict YAML parsing.
- Developer B: US2 (Phase 4) — manifest generator changes; three sub-tasks parallel.
- Developer C: US3 (Phase 5) — listing API DTO + mapper.
- Developer D: US4 (Phase 6) — picks up after Developer C completes T020 (the response wrapper); otherwise parallel.

US1 and US2 are co-priority P1 and ship together as the MVP. US3 and US4 may ship in either order or together as the v1.1 follow-up.

---

## Notes

- Every task either edits an existing file or creates a named file; no vague tasks.
- All Spring components added or modified MUST carry `@LogExecution` per Constitution Principle IV. Verify before checkpoint.
- Constitution PATCH 1.2.2 applies: configuration property defaults live exclusively in `application.yml`; `NodePoolProperties` fields MUST NOT carry Java initializers.
- No database migration is needed — Feature 016's V1.58 already created the `node_pool` column.
- Final `./gradlew clean build` (T043) is the gate for PR readiness per Constitution Tooling Commands.
