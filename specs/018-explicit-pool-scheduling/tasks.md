---
description: "Task list for implementing 018-explicit-pool-scheduling"
---

# Tasks: Explicit Node Pool Scheduling

**Input**: Design documents from `specs/018-explicit-pool-scheduling/`
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

- [ ] T001 Reshape `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolProperties.java` per data-model.md §1: delete `nodePoolLabelKey`, `getLabelSelector(...)`, `GpuSpec`, `CpuSpec`, `MemorySpec`, and the capacity fields (`instance`, `minNodes`, `maxNodes`, `gpu`, `cpu`, `memory`) on `PoolConfig`; add `nodeSelector` (`Map<String,String>`), `affinity` (`io.fabric8.kubernetes.api.model.Affinity`), `tolerations` (`List<io.fabric8.kubernetes.api.model.Toleration>`) fields on `PoolConfig`; add top-level `defaultPool` and `defaultModelPool` (`String`, `@Nullable`); add `isPoolConfigured()` helper. Fields MUST NOT carry Java initializers (Constitution PATCH 1.2.2).
- [ ] T002 Create `src/main/java/com/epam/aidial/deployment/manager/service/manifest/PoolSchedulingPrimitives.java` — Java record with `nodeSelector`, `affinity`, `tolerations` per data-model.md §3, plus `EMPTY` constant, `isEmpty()` method, and `of(PoolConfig)` factory.
- [ ] T003 [P] Update `src/main/resources/application.yml` `app.node-pools` block: remove `label-key`; keep `pools: ${NODE_POOLS:}`; add `default: ${NODE_POOL_DEFAULT:}` and `default-model: ${NODE_POOL_DEFAULT_MODEL:}` per Constitution Configuration property defaults rule (single source of truth in YAML).
- [ ] T004 Rewrite `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolConfiguration.java`: replace JSON ObjectMapper with `YAMLFactory`-backed Jackson; bind `app.node-pools.pools`, `app.node-pools.default`, `app.node-pools.default-model` from `@Value`; parse the YAML into `List<PoolConfig>`; set `defaultPool` / `defaultModelPool` on the bean; preserve the existing duplicate-name check and `@Bean` factory boundary. Leave strict-mode and defaults-validation hooks as TODO callouts to be filled by US1 (T010) and US4 (T026).
- [ ] T005 Adapt `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`: change `resolveNodePoolLabels(...)` from returning `Map<String,String>` to returning `PoolSchedulingPrimitives`; when `nodePool` is non-null, look up the `PoolConfig` and call `PoolSchedulingPrimitives.of(pool)`; when null or pool missing, return `PoolSchedulingPrimitives.EMPTY`. Rename method to `resolvePoolPrimitives` and update its callers. Remove the now-unused `getLabelSelector` import.
- [ ] T006 Adapt `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java` validation hook: replace `nodePoolProperties.exists(nodePool)` (line ~397) with a name-presence check against `nodePoolProperties.findByName(nodePool)`, surfacing the same 400 error when the name doesn't resolve. Keep validation behaviour identical for now; cascade wiring comes in US4 (T029).
- [ ] T007 Delete obsolete Feature 016 DTOs: `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/GpuSpecDto.java`, `CpuSpecDto.java`, `MemorySpecDto.java`. Remove their `@Schema` references from any wiring (NodePoolDto, NodePoolDtoMapper) — these are touched again in US3 (T018), but deleting now keeps the codebase from referencing dead types.
- [ ] T008 Confirm `./gradlew compileJava compileTestJava` succeeds with the foundational changes in place. (Manual checkpoint — no file edits.)

**Checkpoint**: Compile-clean against the new schema. No behavioural changes yet — pool primitives are loaded into POJOs but neither strictly validated nor projected onto workloads, and the listing API still uses the (now half-updated) DTOs. User stories take over from here.

---

## Phase 3: User Story 1 — Configure Pools With Explicit Scheduling Primitives (Priority: P1) 🎯 MVP

**Goal**: An operator declares pools in `NODE_POOLS` (YAML, or JSON-as-YAML) and the application loads them with strict schema validation. Deprecated Feature 016 fields fail startup with a clear error; unknown K8s sub-fields fail startup; duplicate pool names fail startup.

**Independent Test**: Set `NODE_POOLS` to a YAML document carrying every combination of `nodeSelector` / `affinity` / `tolerations`; start the app; confirm pools load. Then introduce each failure mode (`maxNodes` field, unknown operator, duplicate name, `NODE_POOL_LABEL_KEY` set) one at a time; confirm startup fails with the named error.

### Implementation for User Story 1

- [ ] T009 [US1] In `NodePoolConfiguration.java`, configure the YAML `ObjectMapper` with `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = true` and `MapperFeature.PROPAGATE_TRANSIENT_MARKER` defaults; ensure Fabric8 model types (`Affinity`, `Toleration`) deserialize correctly from the YAML structure per research.md §1 and §2.
- [ ] T010 [US1] In `NodePoolConfiguration.java`, add a pre-deserialization scan that parses the raw YAML into `JsonNode` and inspects each pool entry for the deprecated Feature 016 keys (`maxNodes`, `minNodes`, `instance`, `gpu`, `cpu`, `memory`). On any match, throw `IllegalArgumentException` with the message format documented in `contracts/node-pools-api.md` §8 (naming the pool and the deprecated field).
- [ ] T011 [US1] In `NodePoolConfiguration.java`, add a startup check for the `NODE_POOL_LABEL_KEY` environment variable (read directly via `@Value` on the configuration class). If set non-blank, throw `IllegalArgumentException` with the message `NODE_POOL_LABEL_KEY is no longer supported. Pool selection is now expressed via per-pool nodeSelector/affinity/tolerations. See docs/configuration.md.`
- [ ] T012 [US1] Preserve and tighten the existing cross-pool validation in `NodePoolConfiguration.java`: name uniqueness (existing), name non-blank (existing via `@NotBlank`), `@Valid` cascade onto Fabric8 sub-models. Verify Jackson surface validation errors carry the offending JSON path; if not, wrap with `IllegalArgumentException` adding the path prefix.
- [ ] T013 [P] [US1] Update unit test class `src/test/java/com/epam/aidial/deployment/manager/configuration/NodePoolConfigurationTest.java` to cover Story 1 acceptance scenarios: pool with only nodeSelector, pool with only affinity, pool combining all three primitives, duplicate name → fail, invalid K8s schema → fail, deprecated field → fail with specific message, `NODE_POOL_LABEL_KEY` set → fail, single-line JSON parses equivalently to multi-line YAML, YAML comments are ignored.

**Checkpoint**: `NODE_POOLS` parses correctly under the new schema. Strict-mode rejection of legacy fields and unknown K8s sub-fields works. The cascade and the manifest projection are not yet wired — those follow in US4 and US2 respectively.

---

## Phase 4: User Story 2 — Deployments Use The Pool's Explicit Primitives At Deploy Time (Priority: P1) 🎯 MVP

**Goal**: When a deployment carrying a non-null `nodePool` is activated, the workload's pod template (or CRD pod-template equivalent for NIM / KServe-Inference) carries the pool's `nodeSelector`, `affinity`, and `tolerations` verbatim. Null `nodePool` produces no pool-derived primitives.

**Independent Test**: Pick a deployment with `nodePool` set to a pool combining all three primitives; deploy it; inspect the resulting Knative Service / NIMService / KServe InferenceService; verify the three sections match the pool's configuration. Repeat with a deployment carrying `nodePool: null` and verify no pool-derived primitives appear. (Quickstart §5.)

### Implementation for User Story 2

- [ ] T014 [P] [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/manifest/KnativeManifestGenerator.java`: change the `nodePoolLabels` parameter on the generation method from `@Nullable Map<String, String>` to `PoolSchedulingPrimitives primitives`; replace the existing `buildNodePoolAffinity(...)` private helper with logic that projects `primitives.nodeSelector()` onto the Knative `RevisionSpec.nodeSelector`, `primitives.affinity()` onto `RevisionSpec.affinity`, and appends `primitives.tolerations()` to `RevisionSpec.tolerations`. When any of the three is null, leave the corresponding slot untouched. Replace operations are verbatim (FR-006).
- [ ] T015 [P] [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java` analogously to T014: accept `PoolSchedulingPrimitives`; project `nodeSelector` to the NIMService spec's `nodeSelector` slot; project `affinity` to the spec's `affinity` slot; append `tolerations` to the spec's `tolerations` list.
- [ ] T016 [P] [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/manifest/InferenceManifestGenerator.java` analogously: accept `PoolSchedulingPrimitives`; project each section to the KServe `InferenceService` predictor's corresponding pod-template slot.
- [ ] T017 [US2] Update the manifest-generator callers in `AbstractDeploymentManager.java` / `KnativeDeploymentManager.java` / `NimDeploymentManager.java` / `InferenceDeploymentManager.java` to pass `PoolSchedulingPrimitives` (resolved by T005) through to the generators instead of `Map<String, String>`. Confirm `KnativeDeploymentManager`, `NimDeploymentManager`, `InferenceDeploymentManager` constructor signatures remain unchanged.
- [ ] T018 [P] [US2] Update unit tests `src/test/java/com/epam/aidial/deployment/manager/service/manifest/KnativeManifestGeneratorTest.java`, `NimManifestGeneratorTest.java`, `InferenceManifestGeneratorTest.java`: cover Story 2 acceptance scenarios — pool with only nodeSelector → pod template has only nodeSelector; pool with only affinity → pod template has only affinity; pool with all three → pod template has all three; null primitives → pod template untouched (no pool-derived slots).

**Checkpoint**: MVP complete. Pools defined per US1 are projected onto live workloads per US2. The system is end-to-end functional for the headline use case. US3 and US4 layer admin-experience and FE-pre-fill improvements on top.

---

## Phase 5: User Story 3 — Listing API Exposes The Pool's Scheduling Primitives (Priority: P2)

**Goal**: `GET /api/v1/node-pools` returns each pool's `name`, optional `description`, and the scheduling primitives it declared, with fields omitted (not nulled) when absent. The Feature 020 `?includeUtilization=true` contract is unchanged.

**Independent Test**: Call the endpoint with two pools configured (one minimal, one with all three primitives); inspect the JSON; verify only declared fields appear; confirm `?includeUtilization=true` still layers in the utilisation block.

### Implementation for User Story 3

- [ ] T019 [P] [US3] Reshape `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodePoolDto.java` per data-model.md §4: drop the Feature 016 capacity fields and keep only `name`, `description`, `nodeSelector`, `affinity`, `tolerations`. Annotate the record with `@JsonInclude(JsonInclude.Include.NON_EMPTY)` and tighten the `@Schema` annotations.
- [ ] T020 [P] [US3] Create `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodePoolListResponseDto.java` per data-model.md §4: top-level `{pools, defaults}` shape with nested `DefaultsDto`. Annotate with `@JsonInclude(NON_EMPTY)` so absent defaults and empty `pools` collapse to field omission rather than `null` or `{}`. Note: the `defaults` block is populated by US4 (T028) — for US3, the field exists but is always omitted because the env vars aren't yet wired.
- [ ] T021 [US3] Update `src/main/java/com/epam/aidial/deployment/manager/web/mapper/NodePoolDtoMapper.java`: change the return type of the existing mapping method to `NodePoolListResponseDto` (or add a new method); map `List<PoolConfig> → List<NodePoolDto>` preserving order; wrap into `NodePoolListResponseDto` with `defaults = null` for now. MapStruct interface keeps `componentModel = "spring"` per Constitution Key Patterns.
- [ ] T022 [US3] Update `src/main/java/com/epam/aidial/deployment/manager/web/controller/NodePoolController.java`: change the endpoint's return type from `List<NodePoolDto>` to `NodePoolListResponseDto`. Update `@Operation` summary to reflect the new shape. Refresh `@ApiResponse(200, ...)` to point at the wrapper.
- [ ] T023 [P] [US3] Update `src/test/java/com/epam/aidial/deployment/manager/web/controller/NodePoolControllerTest.java`: cover Story 3 acceptance scenarios — two pools with different primitive subsets → response reflects each pool's actual primitives, no defaults; absent fields are omitted from JSON; the `?includeUtilization=true` query parameter continues to return Feature 020's utilization block.

**Checkpoint**: The listing API is on the new shape. Operators can audit the configuration from a single API call. The `defaults` block is reserved but unpopulated — US4 fills it.

---

## Phase 6: User Story 4 — Stamp Fallback Defaults For Workloads Without An Explicit Pool (Priority: P2)

**Goal**: Operators set `NODE_POOL_DEFAULT` and/or `NODE_POOL_DEFAULT_MODEL`. New deployments without an explicit `nodePool` payload field have the appropriate default stamped onto their record at create time and visible immediately in the response. Admin changes to either env var do not migrate existing deployments. Duplicate copies source's value verbatim. Export strips `nodePool`. The listing API surfaces the configured defaults.

**Independent Test**: Configure both defaults, create a NIM deployment without `nodePool` (expect model override stamped), create an MCP deployment without `nodePool` (expect catch-all stamped), create with explicit `nodePool: null` (expect null stored). Change `NODE_POOL_DEFAULT_MODEL`, restart, redeploy the original NIM deployment — confirm its stored `nodePool` did not change. Duplicate the deployment — confirm the duplicate carries the same value, not the new default. Export and inspect: `nodePool` field absent. (Quickstart §4–§8.)

### Implementation for User Story 4

- [ ] T024 [US4] In `NodePoolConfiguration.java`, populate the `defaultPool` and `defaultModelPool` fields on the `NodePoolProperties` bean from the `app.node-pools.default` and `app.node-pools.default-model` `@Value` injections (already declared empty-default in `application.yml` per T003).
- [ ] T025 [US4] In `NodePoolConfiguration.java`, add the startup defaults-validation pass per research.md §5: after pools are loaded and cross-pool validation completes, for each non-null `defaultPool` / `defaultModelPool` value, verify the named pool exists in the loaded catalogue; throw `IllegalArgumentException` with the message format `<ENV_VAR> references node pool '<name>' which is not present in NODE_POOLS.` per contracts §8.
- [ ] T026 [P] [US4] Create `src/main/java/com/epam/aidial/deployment/manager/service/nodepool/WorkloadClassifier.java` per data-model.md §6: final utility class with private constructor; static method `isModelWorkload(DeploymentType)` returning `true` for NIM and KServe-Inference deployment types, `false` otherwise. Verify the exact enum constant names against the codebase's `DeploymentType` enum before committing.
- [ ] T027 [US4] Extend `src/main/java/com/epam/aidial/deployment/manager/service/nodepool/NodePoolService.java`: add `resolveForCreate(DeploymentType type, JsonNullable<String> suppliedNodePool)` that returns the stamped value per FR-018's cascade — if `suppliedNodePool.isPresent()` return its value verbatim (including null); otherwise apply: model-override → catch-all → null. Add `getDefaults()` returning the current `{defaultPool, defaultModelPool}` pair (for mapper consumption). Keep `getNodePools()` returning the pool catalogue.
- [ ] T028 [US4] Update `src/main/java/com/epam/aidial/deployment/manager/web/mapper/NodePoolDtoMapper.java` to populate the `defaults` block in `NodePoolListResponseDto` from `NodePoolService.getDefaults()`. Empty defaults block (both fields unset) MUST serialize as field omission via `@JsonInclude(NON_EMPTY)`.
- [ ] T029 [US4] Add a `JsonNullable<String> nodePool` field to the deployment create / update request records (the existing DTOs under `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/` — exact filename depends on existing structure; locate via grep on the controller's signature). Update the existing service mapper to translate `JsonNullable` into a directive understandable by the service layer (e.g., a small internal `NodePoolDirective {absent, explicitNull, value(name)}` sum type, or just pass the `JsonNullable` through).
- [ ] T030 [US4] Update `DeploymentService.create(...)`: before persisting, invoke `nodePoolService.resolveForCreate(deploymentType, payload.nodePool())`; assign the result to the entity's `nodePool` field. Non-null name still validated against `NODE_POOLS` (T006 hook). Confirm the create response carries the stamped value back to the caller (FR-013).
- [ ] T031 [US4] Update `DeploymentService.update(...)`: when the request payload's `nodePool` `JsonNullable.isPresent()` is false, leave the stored value unchanged; otherwise validate (null is always legal; non-null name must resolve in `NODE_POOLS`) and update. Confirm the cascade does NOT run on update (FR-019).
- [ ] T032 [US4] Update the deployment duplicate flow (likely `DeploymentService.duplicate(...)` or a dedicated `DeploymentDuplicationService` — locate via grep) to copy the source's `nodePool` verbatim into the new record without invoking the cascade (FR-020). Apply the same validation as create — a duplicate of a deployment whose source pool no longer exists returns 400 per contracts §5.
- [ ] T033 [US4] Update the deployment export DTO to omit `nodePool` (FR-021). If the export DTO is the same record type as the internal API response, add `@JsonIgnore` only on the export serialization path (e.g. via a Jackson view, a `MixIn`, or a dedicated export DTO record). Verify the import path does NOT declare `nodePool` — incoming legacy exports' `nodePool` will be silently dropped by Jackson's default unknown-property handling.
- [ ] T034 [P] [US4] Update unit tests for `NodePoolConfigurationTest.java` (startup defaults validation: undefined-default → fail, valid default → pass, same pool as both → pass, defaults set with NODE_POOLS empty → fail).
- [ ] T035 [P] [US4] Update or create `src/test/java/com/epam/aidial/deployment/manager/service/nodepool/NodePoolServiceTest.java`: cover Story 4 acceptance scenarios 1–11 — all four configuration combinations (neither/catch-all only/model only/both), explicit null, explicit name, duplicate flow, admin-changes-default non-retroactivity. Also cover `WorkloadClassifier` edge cases.
- [ ] T036 [P] [US4] Update `src/test/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentServiceTest.java`: cover the create-time cascade integration, the no-cascade-on-update semantics, the duplicate-copies-verbatim behaviour, and the validation rejection for unknown pool names.
- [ ] T037 [P] [US4] Update `NodePoolControllerTest.java` (already touched in T023): add an assertion path covering the `defaults` block when defaults are configured (model + default), when only one is configured, and when neither is configured (block omitted).

**Checkpoint**: Defaults stamp on create and are visible to the user. Admin env-var changes never retroactively migrate. Duplicate preserves source verbatim. Export omits `nodePool`. Listing API surfaces the current defaults. The feature is fully functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T038 [P] Rewrite the **Node Pool Configuration** section of `docs/configuration.md` per the spec's Documentation Deliverables section and research.md §11. MUST include: the three configuration entries with property keys + env vars + defaults + required-when conditions; the YAML document shape with per-field table; a complete worked example (matching the spec's user description in the Input section); the startup validation rules (strict parsing, defaults must reference existing pools, `NODE_POOL_LABEL_KEY` rejection); and the operator-facing note that pool primitives are resolved by name at deploy time (so an admin edit to a pool's `nodeSelector` / `affinity` / `tolerations` propagates on next redeploy to every existing deployment referencing that pool, while changing a default env var never migrates existing deployments — the asymmetry must be made explicit).
- [ ] T039 [P] Confirm OpenAPI `@Schema` documentation on `NodePoolDto`, `NodePoolListResponseDto`, the deployment create / update request DTOs reflects the new field semantics (omit / null / value rules for `nodePool`; `defaults` block omission rule). Run `./gradlew bootRun`, hit `/v3/api-docs`, and inspect.
- [ ] T040 Run `./gradlew checkstyleMain checkstyleTest` and resolve any violations introduced by the schema reshape / new files.
- [ ] T041 Run `./gradlew testFast` to verify the H2-only test suite passes end-to-end.
- [ ] T042 Run the manual smoke test in `specs/018-explicit-pool-scheduling/quickstart.md` §1–§9 against a local app instance. Verify each section behaves as documented.
- [ ] T043 Final `./gradlew clean build` to confirm the full suite (including PostgreSQL + SQL Server Testcontainers) passes.

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
