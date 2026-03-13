# Tasks: Store Deployment Service Name

**Input**: Design documents from `/specs/004-store-service-name/`
**Prerequisites**: plan.md, spec.md, data-model.md, research.md, quickstart.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Add `serviceName` field to entity, domain model, mappers, and repository — shared infrastructure that all user stories depend on.

**CRITICAL**: No user story work can begin until this phase is complete.

- [x] T001 [P] Add `serviceName` field to `DeploymentEntity` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/DeploymentEntity.java` — add `@Column(name = "service_name", length = 63, unique = true) private String serviceName;`
- [x] T002 [P] Add `serviceName` field to `Deployment` domain model in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Deployment.java` (and verify Lombok `@SuperBuilder` propagates to subclasses)
- [x] T003 [P] Update `PersistenceDeploymentMapper` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java` — ensure `serviceName` is mapped in `toDomain`, `toEntity`, and `updateEntityFromDomain`
- [x] T004 Add `findByServiceName(String serviceName)` query to `DeploymentJpaRepository` in `src/main/java/com/epam/aidial/deployment/manager/dao/jpa/DeploymentJpaRepository.java`
- [x] T005 Add `getByServiceName(String serviceName)` wrapper to `DeploymentRepository` in `src/main/java/com/epam/aidial/deployment/manager/dao/repository/DeploymentRepository.java` — follows existing `getById` pattern with `PersistenceDeploymentMapper.toDomain()`

**Checkpoint**: Entity, domain model, mappers, and repository ready. All user stories can now proceed.

---

## Phase 2: User Story 3 - Backward-Compatible Migration (Priority: P1)

**Goal**: Add `service_name` column to the deployment table and backfill existing active deployments with their correct K8s service names, using the configured `resourceNamePrefix`.

**Independent Test**: Upgrade from the previous version and verify all deployed (non-NOT_DEPLOYED/STOPPED) deployments have correct service names, while inactive deployments remain NULL.

### Implementation for User Story 3

- [x] T006 [P] [US3] Create SQL migration `src/main/resources/db/migration/H2/V1.52__AddServiceNameColumn.sql` — `ALTER TABLE deployment ADD COLUMN service_name VARCHAR(63);` + `CREATE UNIQUE INDEX idx_deployment_service_name ON deployment(service_name);`
- [x] T007 [P] [US3] Create SQL migration `src/main/resources/db/migration/POSTGRES/V1.52__AddServiceNameColumn.sql` — same DDL as H2
- [x] T008 [P] [US3] Create SQL migration `src/main/resources/db/migration/MS_SQL_SERVER/V1.52__AddServiceNameColumn.sql` — `ALTER TABLE deployment ADD service_name VARCHAR(63);` + filtered unique index `WHERE service_name IS NOT NULL`
- [x] T009 [US3] Create Java migration base class `src/main/java/db/migration/common/V1_53__BackfillServiceNameBase.java` — extends `BaseJavaMigration`, reads `RESOURCE_NAME_PREFIX` via `System.getenv()`, queries deployments with status NOT IN ('NOT_DEPLOYED', 'STOPPED') AND service_name IS NULL, determines type by checking subtable existence (mcp_deployment/adapter_deployment/interceptor_deployment → MCP prefix, nim_deployment → MCP prefix, inference_deployment → DM prefix), generates `{prefix}-{deploymentId}` and batch updates
- [x] T010 [P] [US3] Create H2-specific migration `src/main/java/db/migration/H2/V1_53__BackfillServiceName.java` — extends `V1_53__BackfillServiceNameBase`
- [x] T011 [P] [US3] Create Postgres-specific migration `src/main/java/db/migration/POSTGRES/V1_53__BackfillServiceName.java` — extends `V1_53__BackfillServiceNameBase`
- [x] T012 [P] [US3] Create SQL Server-specific migration `src/main/java/db/migration/MS_SQL_SERVER/V1_53__BackfillServiceName.java` — extends `V1_53__BackfillServiceNameBase`

**Checkpoint**: Migration tested — `./gradlew testFast` passes with H2. Existing active deployments have service names; NOT_DEPLOYED/STOPPED remain NULL.

---

## Phase 3: User Story 2 - New Deployments Persist Service Name (Priority: P1)

**Goal**: Generate and persist the K8s service name on first `deploy()` using the unified `generateName` convention. Move `getServiceName()` to `AbstractDeploymentManager` to read the stored name. Update `DisposableResourceManager` to use stored names.

**Independent Test**: Create a new deployment of each type, deploy it, and verify the service name is persisted in the DB and used for all K8s operations.

### Implementation for User Story 2

- [x] T013 [US2] Refactor `AbstractDeploymentManager` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java` — (1) move `getServiceName(String id)` here as a concrete method that reads `serviceName` from the deployment record, (2) in the `deploy()` method, before K8s resource creation: if deployment has no `serviceName`, generate via `K8sNamingUtils.generateName(id)` and persist to DB, otherwise reuse existing, (3) ensure `undeploy()`, `reconcile()`, `rollingUpdate()` all use stored `serviceName` via `getServiceName()`
- [x] T014 [P] [US2] Remove `getServiceName()` override from `KnativeDeploymentManager` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManager.java`
- [x] T015 [P] [US2] Remove `getServiceName()` override from `NimDeploymentManager` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java`
- [x] T016 [P] [US2] Remove `getServiceName()` override from `InferenceDeploymentManager` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java`
- [x] T017 [US2] Update `DisposableResourceManager` in `src/main/java/com/epam/aidial/deployment/manager/cleanup/resource/DisposableResourceManager.java` — replace `generateServiceName(id, kind)` with accepting the stored service name as a parameter. Update `saveKnativeServiceResource`, `saveNimServiceResource`, `saveInferenceServiceResource` and their cleanup counterparts to accept service name instead of generating it. Remove the `generateServiceName` method.

**Checkpoint**: New deployments of all types generate unified service names, persist them, and use them for K8s operations. Undeploy + redeploy reuses the same stored name.

---

## Phase 4: User Story 1 - Resilient Deployment Lookup After Prefix Change (Priority: P1)

**Goal**: Ensure all K8s operations use the stored service name so that changing `resourceNamePrefix` does not orphan existing deployments. This story validates the end-to-end behavior of US2 (stored names) + US3 (migration).

**Independent Test**: Deploy a service, change `resourceNamePrefix`, restart, and verify the deployment is still managed correctly.

**Depends on**: US2 (Phase 3) + US3 (Phase 2)

### Implementation for User Story 1

- [x] T018 [US1] Verify and update all callers of `getServiceName()` in `AbstractDeploymentManager` (`src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`) — audit `deploy()`, `undeploy()`, `reconcile()`, `rollingUpdate()`, `updateCiliumNetworkPolicy()`, `stopOnServiceNotFound()` to ensure they all use the stored service name from the deployment record (not derived from `K8sNamingUtils`). Fix any remaining usages that still derive the name.
- [x] T019 [US1] Verify `AbstractModelDeploymentManager` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractModelDeploymentManager.java` — ensure any K8s operations (pod lookups, container resource for logs) use stored service name where applicable.

**Checkpoint**: After prefix change and restart, all existing deployments remain associated with their K8s resources. Status queries return correct state using stored names.

---

## Phase 5: User Story 4 - K8s Event Reconciliation Using Stored Names (Priority: P2)

**Goal**: Replace `IdExtractor`-based event handling with service name lookup in the database. Events match deployments by `service_name` column instead of parsing deployment ID from the K8s resource name.

**Independent Test**: Trigger K8s events for a service whose name doesn't match the current naming convention and verify the system still reconciles correctly.

**Depends on**: Phase 1 (Foundational — `getByServiceName` repository query)

### Implementation for User Story 4

- [x] T020 [US4] Refactor `AbstractResourceEventHandler` in `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/AbstractResourceEventHandler.java` — replace `IdExtractor`-based ID extraction with a repository lookup by service name. In `processEvent()`: extract resource name from K8s metadata, call `DeploymentRepository.getByServiceName(resourceName)` to find the deployment, pass the deployment ID to `triggerReconcile()`. If no deployment found, log warning and skip (same behavior as current blank ID case). Remove `IdExtractor` constructor parameter.
- [x] T021 [P] [US4] Update `KnativeServiceEventHandler` in `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/KnativeServiceEventHandler.java` — remove `K8sNamingUtils::extractMcpPrefixedId` IdExtractor reference from constructor call
- [x] T022 [P] [US4] Update `NimServiceEventHandler` in `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/NimServiceEventHandler.java` — remove `K8sNamingUtils::extractMcpPrefixedId` IdExtractor reference from constructor call
- [x] T023 [P] [US4] Update `InferenceServiceEventHandler` in `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/InferenceServiceEventHandler.java` — remove `K8sNamingUtils::extractId` IdExtractor reference from constructor call
- [x] T024 [US4] Delete `IdExtractor` interface at `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/IdExtractor.java`

**Checkpoint**: K8s events are matched to deployments by stored service name. Events for unknown service names are ignored gracefully. IdExtractor is fully removed.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Remove legacy naming code, verify checkstyle, run full test suite.

- [x] T025 Remove `extractMcpPrefixedId`, `extractId`, and `generateMcpPrefixedName` methods from `K8sNamingUtils` in `src/main/java/com/epam/aidial/deployment/manager/utils/K8sNamingUtils.java`. Also remove the `MCP_PREFIX` constant if no longer used. Keep `generateName`, `generateName(type, name)`, `generateUniqueName`, `extractName`.
- [x] T026 Remove or update any remaining references to deleted methods — search codebase for `extractMcpPrefixedId`, `extractId`, `generateMcpPrefixedName`, `IdExtractor` and fix compilation errors
- [x] T027 Run `./gradlew checkstyleMain checkstyleTest` and fix any style violations
- [x] T028 Run `./gradlew testFast` and fix any test failures — update existing functional tests that may assert on old naming conventions or IdExtractor behavior

**Checkpoint**: All legacy naming code removed. Checkstyle passes. Fast tests pass. Codebase compiles cleanly with `-Werror`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately
- **US3 Migration (Phase 2)**: Depends on Phase 1 (entity must have serviceName field for JPA validation)
- **US2 Deploy Flow (Phase 3)**: Depends on Phase 1 (entity/repository)
- **US1 Resilient Lookup (Phase 4)**: Depends on Phase 2 (migration) + Phase 3 (deploy flow)
- **US4 Event Reconciliation (Phase 5)**: Depends on Phase 1 (getByServiceName query)
- **Polish (Phase 6)**: Depends on Phases 3, 4, 5 (all code changes complete before removing legacy code)

### User Story Dependencies

- **US3 (P1)**: Can start after Phase 1 — independent of other stories
- **US2 (P1)**: Can start after Phase 1 — independent of US3 (but benefits from migration data)
- **US1 (P1)**: Depends on US2 + US3 — validates end-to-end behavior
- **US4 (P2)**: Can start after Phase 1 — independent of US2/US3 but logically follows them

### Within Each User Story

- DDL migrations before Java migrations (Flyway version ordering)
- Base migration class before vendor-specific subclasses
- AbstractDeploymentManager changes before child class cleanup
- DisposableResourceManager after deploy flow changes
- AbstractResourceEventHandler before child handler updates

### Parallel Opportunities

**Phase 1**: T001, T002, T003 can run in parallel (different files)
**Phase 2**: T006, T007, T008 in parallel (DDL); T010, T011, T012 in parallel (vendor subclasses)
**Phase 3**: T014, T015, T016 in parallel (child manager cleanup)
**Phase 5**: T021, T022, T023 in parallel (child handler updates)

---

## Parallel Example: Phase 2 (US3 Migration)

```text
# Launch DDL migrations in parallel:
T006: H2 SQL migration V1.52
T007: Postgres SQL migration V1.52
T008: SQL Server SQL migration V1.52

# Then base class:
T009: Java migration base class V1.53

# Then vendor subclasses in parallel:
T010: H2 Java migration V1.53
T011: Postgres Java migration V1.53
T012: SQL Server Java migration V1.53
```

---

## Implementation Strategy

### MVP First (US3 + US2 — Migration + Deploy Flow)

1. Complete Phase 1: Foundational (entity, DAO, mappers)
2. Complete Phase 2: US3 Migration (column + backfill)
3. Complete Phase 3: US2 Deploy Flow (generate + persist + use stored name)
4. **STOP and VALIDATE**: `./gradlew testFast` — existing deployments have service names, new deployments generate and store them
5. This delivers the core value: service names are stored and used

### Incremental Delivery

1. Phase 1 + Phase 2 (US3) → Migration works independently → validate with testFast
2. Add Phase 3 (US2) → New deployments use stored names → validate
3. Add Phase 4 (US1) → Full resilience verified → validate end-to-end
4. Add Phase 5 (US4) → Event reconciliation uses stored names → validate
5. Phase 6 → Polish, remove legacy code → `./gradlew clean build`

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US1 has minimal unique code tasks (mostly validation of US2+US3) — its value is confirming end-to-end resilience
- US2 and US4 can be developed in parallel after Phase 1
- Migration (US3) should be validated first since it's the foundation for all runtime behavior
- `./gradlew testFast` after each phase; `./gradlew clean build` for final validation
