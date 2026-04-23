# Tasks: Node Pool Selector

**Input**: Design documents from `/specs/016-node-pool-selector/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/node-pools-api.md

**Tests**: Not explicitly requested — test tasks omitted. Functional tests included as implementation tasks where needed for verification.

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story (US1, US2, US3, US4)
- Exact file paths included in descriptions

---

## Phase 1: Setup

**Purpose**: No project initialization needed — this is a feature addition to an existing project. Phase intentionally empty.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Configuration model and K8s client extensions that ALL user stories depend on.

**CRITICAL**: No user story work can begin until this phase is complete.

- [x] T001 [P] Create `NodePoolProperties` configuration class with `List<NodePoolConfig>` (each with name, description, maxNodes, labelSelector map, nodeSpec with cpuMillis/memoryBytes/gpu) in `src/main/java/com/epam/aidial/deployment/manager/configuration/NodePoolProperties.java`. Use `@ConfigurationProperties(prefix = "app")` with a `nodePools` list field (no default initializer per constitution). Add `@LogExecution` annotation.

- [x] T002 [P] Add `node-pools` configuration to `src/main/resources/application.yml` under the `app:` section. Define an empty list as default: `node-pools: []`. Include inline comments documenting the structure (name, description, max-nodes, label-selector map, node-spec with cpu-millis/memory-bytes/gpu). Use `${ENV_VAR:default}` pattern if applicable.

- [x] T003 [P] Add `listNodes(Map<String, String> labelSelector)` method to `src/main/java/com/epam/aidial/deployment/manager/kubernetes/K8sClient.java`. Uses Fabric8 `client.nodes().withLabels(labelSelector).list()` to return `List<Node>`. Also add `listAllPodsOnNode(String nodeName)` method that lists all pods across all namespaces scheduled on a given node using field selector `spec.nodeName={nodeName}`.

- [x] T004 Run `./gradlew checkstyleMain` to verify foundational changes pass code style.

**Checkpoint**: Configuration model and K8s client ready — user story implementation can begin.

---

## Phase 3: User Story 1 — View Available Node Pools (Priority: P1) MVP

**Goal**: Administrators can call `GET /api/v1/node-pools` and see all configured pools with live per-node K8s utilization data.

**Independent Test**: `curl http://localhost:8080/api/v1/node-pools | jq .` returns configured pools enriched with running node count and per-node allocatable/requested resources.

### Implementation for User Story 1

- [x] T005 [P] [US1] Create `NodeSpecDto` record in `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodeSpecDto.java`. Fields: `cpuMillis` (long), `memoryBytes` (long), `gpu` (int). Include `@Schema` OpenAPI annotations.

- [x] T006 [P] [US1] Create `NodeUtilizationDto` record in `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodeUtilizationDto.java`. Fields: `nodeName` (String), `allocatableCpuMillis` (long), `allocatableMemoryBytes` (long), `allocatableGpu` (int), `requestedCpuMillis` (long), `requestedMemoryBytes` (long), `requestedGpu` (int). Include `@Schema` OpenAPI annotations.

- [x] T007 [P] [US1] Create `NodePoolDto` record in `src/main/java/com/epam/aidial/deployment/manager/web/dto/nodepool/NodePoolDto.java`. Fields: `name` (String), `description` (String, @Nullable), `maxNodes` (int), `runningNodes` (int), `nodeSpec` (NodeSpecDto), `nodes` (List<NodeUtilizationDto>). Include `@Schema` OpenAPI annotations.

- [x] T008 [US1] Create `NodePoolService` in `src/main/java/com/epam/aidial/deployment/manager/service/nodepool/NodePoolService.java`. Inject `NodePoolProperties` and `K8sClient`. Implement `getNodePools()` method that: (1) iterates configured pools, (2) calls `K8sClient.listNodes(labelSelector)` per pool, (3) for each running node calls `K8sClient.listAllPodsOnNode(nodeName)` and sums container resource requests (cpu, memory, nvidia.com/gpu), (4) extracts allocatable resources from node status, (5) returns list of domain objects with config + live data. Add `@Service`, `@LogExecution`, `@Slf4j` annotations. Parse K8s Quantity values for cpu (to millicores) and memory (to bytes) using Fabric8's `Quantity` class.

- [x] T009 [US1] Create `NodePoolDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/NodePoolDtoMapper.java`. MapStruct `@Mapper(componentModel = "spring")` interface that maps service-layer results to `NodePoolDto`, `NodeSpecDto`, `NodeUtilizationDto`. Map config nodeSpec to NodeSpecDto and K8s node data to NodeUtilizationDto.

- [x] T010 [US1] Create `NodePoolController` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/NodePoolController.java`. `@RestController` with `@RequestMapping("/api/v1/node-pools")`. Single `GET` endpoint returning `List<NodePoolDto>`. Add `@Operation(summary = "List available node pools with live utilization")`, `@ApiResponse` annotations. Add `@LogExecution`. Inject `NodePoolService` and `NodePoolDtoMapper`.

- [x] T011 [US1] Run `./gradlew checkstyleMain checkstyleTest` and `./gradlew testFast` to verify US1 compiles and passes existing tests.

**Checkpoint**: GET /api/v1/node-pools is functional. US1 independently testable.

---

## Phase 4: User Story 2 — Select a Node Pool for a Deployment (Priority: P2)

**Goal**: Deployment create/update accepts an optional `nodePool` field validated against configured pools; get/list returns it.

**Independent Test**: Create a deployment with `"nodePool": "gpu-a100-pool"`, retrieve it, verify the field is persisted and returned.

### Implementation for User Story 2

- [x] T012 [P] [US2] Create Flyway migration `V1.57__AddNodePoolColumn.sql` in `src/main/resources/db/migration/H2/`. SQL: `ALTER TABLE deployment ADD COLUMN node_pool VARCHAR(255);`

- [x] T013 [P] [US2] Create Flyway migration `V1.57__AddNodePoolColumn.sql` in `src/main/resources/db/migration/POSTGRES/`. SQL: `ALTER TABLE deployment ADD COLUMN node_pool VARCHAR(255);`

- [x] T014 [P] [US2] Create Flyway migration `V1.57__AddNodePoolColumn.sql` in `src/main/resources/db/migration/MS_SQL_SERVER/`. SQL: `ALTER TABLE deployment ADD node_pool NVARCHAR(255);`

- [x] T015 [P] [US2] Add `nodePool` field (String, nullable, `@Column(name = "node_pool")`) to `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/DeploymentEntity.java`.

- [x] T016 [P] [US2] Add `nodePool` field (String) to domain model `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Deployment.java`.

- [x] T017 [P] [US2] Add `nodePool` field (String) to domain model `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateDeployment.java`.

- [x] T018 [P] [US2] Add `nodePool` field (String, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/DeploymentDto.java`.

- [x] T019 [P] [US2] Add `nodePool` field (String, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateDeploymentRequestDto.java`.

- [x] T020 [US2] Add node pool validation to `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java`. In `createDeployment()` and `updateDeployment()` methods, validate that if `nodePool` is non-null, it matches a configured pool name from `NodePoolProperties`. Throw appropriate exception (400) if pool name is invalid. Inject `NodePoolProperties`.

- [x] T021 [US2] Verify that `PersistenceDeploymentMapper`, `DeploymentMapper`, and `DeploymentDtoMapper` auto-map `nodePool` field by convention (same field name across all layers). If MapStruct doesn't auto-map due to ignored fields or custom mappings, add explicit mapping. Check `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java`, `src/main/java/com/epam/aidial/deployment/manager/mapper/DeploymentMapper.java`, and `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java`.

- [x] T022 [US2] Verify that `duplicateDeployment` in `DeploymentService` copies `nodePool` field (should happen automatically via `DeploymentMapper.toCreateCloneDeployment`). Check `src/main/java/com/epam/aidial/deployment/manager/mapper/DeploymentMapper.java` `toCreateCloneDeployment` method.

- [x] T023 [US2] Run `./gradlew checkstyleMain checkstyleTest` and `./gradlew testFast` to verify US2 compiles, migrations run, and existing tests pass.

**Checkpoint**: Deployment CRUD carries nodePool. US2 independently testable.

---

## Phase 5: User Story 3 — Enforce Node Pool Affinity on Deploy (Priority: P2)

**Goal**: Deploying a deployment with a nodePool sets hard node affinity (`requiredDuringSchedulingIgnoredDuringExecution`) using the pool's label selector. Clearing the pool removes affinity.

**Independent Test**: Deploy a deployment with nodePool set, inspect the generated K8s manifest to verify node affinity `matchExpressions` match the pool's label selector.

### Implementation for User Story 3

- [x] T024 [US3] Add deploy-time node pool validation. In the deploy flow (either `DeploymentService.deploy()` or `AbstractDeploymentManager.deploy()`), before creating K8s resources: if the deployment has a `nodePool` set, validate it still exists in `NodePoolProperties`; if removed, throw validation error (400). Resolve the pool's `labelSelector` map and pass it to `prepareServiceSpec`. Check `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java` and `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`.

- [x] T025 [P] [US3] Add node affinity injection to `src/main/java/com/epam/aidial/deployment/manager/service/manifest/KnativeManifestGenerator.java`. Extend the `serviceConfig` method to accept an optional `Map<String, String> nodePoolLabels` parameter. When non-null, build a `NodeAffinity` with `requiredDuringSchedulingIgnoredDuringExecution` containing `NodeSelectorTerm` with `matchExpressions` (one `In` expression per label key-value). Apply to `RevisionSpec` via the `revisionSpecChain`. When null, ensure no affinity is set (or clear existing).

- [x] T026 [P] [US3] Add node affinity injection to `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`. Extend `serviceConfig` to accept optional `Map<String, String> nodePoolLabels`. Apply `NodeAffinity` with `requiredDuringSchedulingIgnoredDuringExecution` to the NIMService spec's pod template. Research NIMService CRD's pod affinity field (likely in spec or podSpec) and apply accordingly.

- [x] T027 [P] [US3] Add node affinity injection to `src/main/java/com/epam/aidial/deployment/manager/service/manifest/InferenceManifestGenerator.java`. Extend `serviceConfig` to accept optional `Map<String, String> nodePoolLabels`. Apply `NodeAffinity` with `requiredDuringSchedulingIgnoredDuringExecution` to the KServe InferenceService Predictor's node affinity field.

- [x] T028 [US3] Update deployment manager classes to pass the resolved node pool label selector to manifest generators. Modify `KnativeDeploymentManager.prepareServiceSpec()`, `NimDeploymentManager.prepareServiceSpec()`, and `InferenceDeploymentManager.prepareServiceSpec()` to resolve the deployment's `nodePool` name to a `labelSelector` map via `NodePoolProperties` and pass it to the respective manifest generator's `serviceConfig` method. Files: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManager.java`, `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java`, `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java`.

- [x] T029 [US3] Run `./gradlew checkstyleMain checkstyleTest` and `./gradlew testFast` to verify US3 compiles and passes existing tests.

**Checkpoint**: Deploy with nodePool sets hard affinity. US3 independently testable by inspecting generated manifests.

---

## Phase 6: User Story 4 — Clear Node Pool Selection (Priority: P3)

**Goal**: Setting `nodePool` to null on update clears the selection; redeploying removes affinity.

**Independent Test**: Update a deployment to set nodePool=null, retrieve it, verify nodePool is null. Redeploy and verify no pool-specific affinity on the workload.

### Implementation for User Story 4

- [x] T030 [US4] Verify that clearing nodePool (setting to null in update request) works end-to-end: (1) the nullable field in `CreateDeploymentRequestDto` allows null, (2) `DeploymentService.updateDeployment()` accepts null without validation error, (3) `PersistenceDeploymentMapper` maps null through to entity, (4) redeploying passes null to manifest generators which skip affinity. This is a verification task — if any step fails, fix the gap. Files to check: `DeploymentService.java` validation logic (T020), manifest generators (T025–T027) null handling.

- [x] T031 [US4] Run `./gradlew checkstyleMain checkstyleTest` and `./gradlew testFast` to verify US4 passes.

**Checkpoint**: All 4 user stories functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, schema generation, and final verification.

- [x] T032 [P] Update `docs/configuration.md` with new node pool configuration properties: `APP_NODE_POOLS_*` env vars, their structure, default values, and descriptions. Follow existing documentation patterns in the file.

- [x] T033 [P] Run `./gradlew generateDbSchema` to regenerate `docs/db-schema.md` with the new `node_pool` column. Commit the updated file.

- [x] T034 Run `./gradlew clean build` — full clean build with all tests and checkstyle to verify everything passes end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 2 (Foundational)**: No dependencies — can start immediately
- **Phase 3 (US1)**: Depends on Phase 2 (T001–T003) — needs NodePoolProperties and K8sClient
- **Phase 4 (US2)**: Depends on Phase 2 (T001) — needs NodePoolProperties for validation. Independent of US1.
- **Phase 5 (US3)**: Depends on Phase 4 (needs nodePool on deployment model) and Phase 2 (needs NodePoolProperties for label selector resolution)
- **Phase 6 (US4)**: Depends on Phase 4 and Phase 5 (verification of null handling across both)
- **Phase 7 (Polish)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (View Pools)**: Depends on Foundational only — fully independent
- **US2 (Select Pool)**: Depends on Foundational only — fully independent of US1
- **US3 (Enforce Affinity)**: Depends on US2 (needs nodePool field on deployment) — cannot start until T015–T019 complete
- **US4 (Clear Selection)**: Depends on US2 + US3 — verification only

### Within Each User Story

- DTOs before service (service uses DTOs)
- Service before controller (controller injects service)
- Migrations before entity changes (entity references column)
- Manifest generator changes before deployment manager changes (manager calls generator)

### Parallel Opportunities

- **Phase 2**: T001, T002, T003 all in parallel (different files)
- **Phase 3**: T005, T006, T007 in parallel (independent DTOs); T008 after all three
- **Phase 4**: T012–T014 in parallel (3 vendor migrations); T015–T019 in parallel (different model layers)
- **Phase 5**: T025, T026, T027 in parallel (3 independent manifest generators)
- **Phase 7**: T032, T033 in parallel
- **US1 and US2 can proceed in parallel** after Phase 2 completes (independent stories)

---

## Parallel Example: Phase 2 (Foundational)

```
# All three foundational tasks in parallel:
Task T001: "Create NodePoolProperties in configuration/NodePoolProperties.java"
Task T002: "Add node-pools config to application.yml"
Task T003: "Add listNodes/listAllPodsOnNode to K8sClient.java"
```

## Parallel Example: Phase 3 + Phase 4 (US1 + US2 in parallel)

```
# After Phase 2, launch both stories:
# US1 track:
Task T005: "Create NodeSpecDto"
Task T006: "Create NodeUtilizationDto"
Task T007: "Create NodePoolDto"
# then T008 → T009 → T010

# US2 track (parallel to US1):
Task T012: "Migration H2"
Task T013: "Migration Postgres"
Task T014: "Migration SQL Server"
Task T015: "Add nodePool to DeploymentEntity"
# ... through T022
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational (T001–T004)
2. Complete Phase 3: User Story 1 (T005–T011)
3. **STOP and VALIDATE**: `curl /api/v1/node-pools` returns pool data
4. Demo/deploy if ready — admins can already see cluster capacity

### Incremental Delivery

1. Phase 2 → Foundation ready
2. Phase 3 (US1) → Admins can view pools (MVP!)
3. Phase 4 (US2) → Admins can assign pools to deployments
4. Phase 5 (US3) → Deployed workloads respect pool affinity
5. Phase 6 (US4) → Pool selection can be cleared
6. Phase 7 → Docs, schema, final build

### Parallel Team Strategy

With two developers after Phase 2:
- Developer A: US1 (view pools) → US3 (affinity)
- Developer B: US2 (select pool) → US4 (clear) → Polish

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story
- All mappers (T021) should auto-map `nodePool` by name convention — verify and fix if needed
- NIMService CRD affinity field (T026) needs runtime verification against the actual CRD schema
- Commit after each phase checkpoint
