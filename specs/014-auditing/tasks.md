# Tasks: Auditing

**Input**: Design documents from `/specs/014-auditing/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/activities-api.md, quickstart.md

**Organization**: Tasks are grouped by user story. US4 (Track All Resources) and US5 (Historical Snapshots) are infrastructure concerns covered by the Foundational phase. US1+US2 are combined (same endpoint). US3 is a separate phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: Add new dependencies and configuration properties

- [ ] T001 Add `org.hibernate.orm:hibernate-envers` (version managed by Spring Boot BOM) and `com.github.f4b6a3:uuid-creator` dependencies to `build.gradle`
- [ ] T002 [P] Add Envers properties to `src/main/resources/application.yml` — set `spring.jpa.properties.org.hibernate.envers.store_data_at_delete: true`; verify no other Envers defaults need overriding given Spring Boot's physical naming strategy lowercases `_AUD`/`REV`/`REVTYPE` automatically

**Checkpoint**: `./gradlew clean build` compiles with new dependencies, existing tests pass

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Transaction timestamp infrastructure, Envers core entities, entity annotations, migrations, and revision listener. Satisfies US4 (all entities tracked) and US5 (historical snapshots preserved) at the data layer.

**CRITICAL**: No user story API work can begin until this phase is complete.

### Transaction Timestamp Infrastructure

- [ ] T003 [P] Create `TransactionTimestampContext.java` in `src/main/java/com/epam/aidial/deployment/manager/transaction/timestamp/` — `@Component` (no `@LogExecution` — add ArchUnit exclusion) that reads the bound timestamp from `TransactionSynchronizationManager` resource map using key `TRANSACTION_TIMESTAMP_KEY`; throws `IllegalStateException` if no active transaction or timestamp not initialized
- [ ] T004 [P] Create `TransactionTimestampAspect.java` in `src/main/java/com/epam/aidial/deployment/manager/transaction/timestamp/` — `@Aspect` `@Component` (no `@LogExecution` — add ArchUnit exclusion) with `@Before("@annotation(transactional)")` that binds `System.currentTimeMillis()` to `TransactionSynchronizationManager` if not already bound, and registers `TransactionSynchronization.afterCompletion()` to unbind it
- [ ] T005 Update `JpaConfiguration.java` in `src/main/java/com/epam/aidial/deployment/manager/configuration/datasource/` — add `AuditJpaPackage.class` to `@EnableJpaRepositories(basePackageClasses = ...)`, add `AuditEntityPackage.class` to `@EntityScan(basePackageClasses = ...)`, change `@EnableJpaAuditing` to `@EnableJpaAuditing(dateTimeProviderRef = "transactionDateTimeProvider")`, add `@Bean DateTimeProvider transactionDateTimeProvider(TransactionTimestampContext)` that returns `Instant.ofEpochMilli(context.getTimestamp())`

### Core Audit Entities and Enums

- [ ] T006 [P] Create `ActivityType.java` enum in `src/main/java/com/epam/aidial/deployment/manager/model/audit/` — values: `Create`, `Update`, `Delete`
- [ ] T007 [P] Create `ActivityResourceType.java` enum in `src/main/java/com/epam/aidial/deployment/manager/model/audit/` — 11 values: `AdapterDeployment`, `ApplicationDeployment`, `InterceptorDeployment`, `McpDeployment`, `NimDeployment`, `InferenceDeployment`, `AdapterImageDefinition`, `ApplicationImageDefinition`, `InterceptorImageDefinition`, `McpImageDefinition`, `ImageBuildDomainWhitelist`
- [ ] T008 [P] Create `AuditRevisionEntity.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/entity/` — `@Entity`, `@RevisionEntity(AuditRevisionListener.class)`, `@Table(name = "REVINFO")`; fields: `@Id @GeneratedValue(strategy = IDENTITY) @RevisionNumber Integer id`, `@RevisionTimestamp Long timestamp`, `String author`, `String email`, `@OneToMany(cascade = ALL, mappedBy = "revision", orphanRemoval = true) Set<AuditActivityEntity> activities`
- [ ] T009 [P] Create `AuditActivityEntity.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/entity/` — `@Entity`, `@Table`; fields: `@Id @JdbcTypeCode(SqlTypes.VARCHAR) UUID activityId`, `@Enumerated(STRING) ActivityType activityType`, `@Enumerated(STRING) ActivityResourceType resourceType`, `String resourceId`, `Long epochTimestampMs`, `String initiatedAuthor`, `String initiatedEmail`, `Integer revision`
- [ ] T010 [P] Create `AuditEntityPackage.java` marker in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/entity/` and `AuditJpaPackage.java` marker in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/jpa/` — empty interfaces for `@EntityScan` and `@EnableJpaRepositories` scanning

### Entity Class → Resource Type Mapper

- [ ] T011 Create `AuditActivityMapper.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/mapper/` — `@Component` `@LogExecution`; method `mapResourceType(Class<?>)` maps each of the 11 concrete entity classes to their `ActivityResourceType` enum value, throws `IllegalArgumentException` for unmapped classes (including base `DeploymentEntity` and `ImageDefinitionEntity`); method `mapActivityType(RevisionType)` maps ADD→Create, MOD→Update, DEL→Delete

### Add @Audited Annotations to Existing Entities

- [ ] T012 [P] Add `@Audited` annotation to `DeploymentEntity.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/` — class-level annotation; propagates to all 6 subtypes via JOINED inheritance
- [ ] T013 [P] Add `@Audited` annotation to `ImageDefinitionEntity.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/` — class-level annotation; propagates to all 4 subtypes via JOINED inheritance
- [ ] T014 [P] Add `@Audited` annotation to `DomainWhitelistEntity.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/`

### Flyway Migrations

- [ ] T015 Create `V1.55__CreateAuditTables.sql` in `src/main/resources/db/migration/H2/`, `POSTGRES/`, and `MS_SQL_SERVER/` — 16 tables per dialect: `revinfo` (with `id` auto-increment, `timestamp` bigint, `author` varchar(255), `email` varchar(320)); 14 `*_aud` tables matching data-model.md (base `_aud` tables have `rev`, `revtype`, PK, all columns nullable; subclass `_aud` tables have `rev`, PK, subclass columns only); `deployment_topics_aud`; `audit_activity_entity` with FK to revinfo. Use dialect-specific auto-increment syntax: H2 `AUTO_INCREMENT`, Postgres `GENERATED BY DEFAULT AS IDENTITY`, MS SQL `IDENTITY(1,1)`

### Revision Listener

- [ ] T016 Create `AuditRevisionListener.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/listener/` — implements `EntityTrackingRevisionListener` and `ApplicationContextAware`; in `setApplicationContext()`: get `TransactionTimestampContext`, `AuditActivityMapper`, `SecurityClaimsExtractor` beans; in `newRevision()`: set author/email from `SecurityClaimsExtractor` instance methods, set timestamp from `TransactionTimestampContext`, reset ThreadLocal dedup state; in `entityChanged()`: use `AuditActivityMapper` to map entity class → resource type and revision type → activity type, build `AuditActivityEntity` with UUID v7 via `UuidCreator.getTimeOrderedEpoch()`, add to revision's activities set; implement deduplication: if same resource appears multiple times in one transaction, Create/Delete takes precedence over Update

**Checkpoint**: `./gradlew testFast` — Flyway applies V1.55 migration, Hibernate validates schema, existing CRUD operations produce `revinfo` entries, `*_aud` rows, and `audit_activity_entity` rows. All existing tests pass.

---

## Phase 3: US1 + US2 — Activity List with Filtering (Priority: P1)

**Goal**: Expose a paginated, filterable activity list via `POST /api/v1/activities` so administrators can view and search the audit trail.

**Independent Test**: Create/update/delete a deployment, then call `POST /api/v1/activities` with various filter combinations and verify correct results with pagination metadata.

### Domain and Persistence Layer

- [ ] T017 [P] [US1] Create `AuditActivity.java` domain model in `src/main/java/com/epam/aidial/deployment/manager/model/audit/` — `@Data` class with fields: `UUID activityId`, `String activityType`, `String resourceType`, `String resourceId`, `Long epochTimestampMs`, `String initiatedAuthor`, `String initiatedEmail`, `Integer revision`
- [ ] T018 [P] [US1] Create `AuditActivityJpaRepository.java` in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/jpa/` — extends `CrudRepository<AuditActivityEntity, UUID>`, `PagingAndSortingRepository<AuditActivityEntity, UUID>`, `JpaSpecificationExecutor<AuditActivityEntity>`
- [ ] T019 [US1] Create `PersistenceAuditActivityMapper.java` (MapStruct) in `src/main/java/com/epam/aidial/deployment/manager/dao/audit/mapper/` — `@Mapper(componentModel = "spring")`, maps `AuditActivityEntity` → `AuditActivity`

### Web DTOs

- [ ] T020 [P] [US2] Create `PageRequestDto.java` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/audit/` — request body DTO per `contracts/activities-api.md`: `Integer page` (default 0), `Integer size` (default 20), nested `SortDto sort` (field + direction), `List<FilterDto> filters`; `FilterDto` supports two shapes: equality filters (`field` + `value`) for string fields and range filters (`field` + `from`/`to`) for `epochTimestampMs`; add `@Valid` constraints
- [ ] T021 [P] [US2] Create `PageDto.java` generic in `src/main/java/com/epam/aidial/deployment/manager/web/dto/audit/` — `@Data @Builder` with `List<T> data`, `long total`, `int totalPages`
- [ ] T022 [P] [US1] Create `AuditActivityDto.java` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/audit/` — response DTO per `contracts/activities-api.md`: `UUID activityId`, `String activityType`, `String resourceType`, `String resourceId`, `Long epochTimestampMs`, `String initiatedAuthor`, `String initiatedEmail`, `Integer revision`

### Web Mapper

- [ ] T023 [US1] Create `AuditActivityDtoMapper.java` (MapStruct) in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/` — `@Mapper(componentModel = "spring")`, maps `AuditActivity` → `AuditActivityDto`

### Service

- [ ] T024 [US1] Create `AuditActivityService.java` in `src/main/java/com/epam/aidial/deployment/manager/service/audit/` — `@Service` `@LogExecution` `@RequiredArgsConstructor`; method `getActivitiesList(...)` accepts pagination/sort/filter parameters, converts filter criteria to `Specification<AuditActivityEntity>`: case-insensitive equality matching on `activityId`, `activityType`, `resourceType`, `resourceId`, `initiatedAuthor`, `initiatedEmail`, and range matching (>=from, <=to) on `epochTimestampMs`; queries `AuditActivityJpaRepository.findAll(specification, pageable)`; maps results via `PersistenceAuditActivityMapper`; returns domain page result. Method `getActivity(UUID activityId)` finds by ID, throws `EntityNotFoundException` if not found.

### Controller

- [ ] T025 [US1] Create `AuditActivityController.java` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/` — `@RestController` `@LogExecution` `@RequestMapping("/api/v1")` `@RequiredArgsConstructor` `@Validated`; `@PostMapping("/activities")` endpoint accepting `@RequestBody @Valid PageRequestDto`, delegates to `AuditActivityService`, maps results to `PageDto<AuditActivityDto>` using `AuditActivityDtoMapper`; add `@Operation(summary = "List activities with pagination, sorting, and filtering")` and `@ApiResponse` annotations

**Checkpoint**: `POST /api/v1/activities` returns paginated, filterable activity list. Creating/updating/deleting any audited entity produces activity records queryable through this endpoint.

---

## Phase 4: US3 — Single Activity Detail (Priority: P2)

**Goal**: Expose a detail endpoint to retrieve a single activity by UUID.

**Independent Test**: Create a deployment, capture the resulting activity ID from the list endpoint, retrieve it via `GET /api/v1/activities/{activityId}`, verify all fields match.

- [ ] T026 [US3] Add `@GetMapping("/activities/{activityId}")` endpoint to `AuditActivityController.java` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/` — accepts `@PathVariable UUID activityId`, delegates to `AuditActivityService.getActivity(activityId)`, maps result via `AuditActivityDtoMapper`, returns `AuditActivityDto`; returns 404 via `EntityNotFoundException` when not found; add `@Operation(summary = "Get a single activity by ID")` annotation

**Checkpoint**: `GET /api/v1/activities/{activityId}` returns a single activity record. Non-existent IDs return 404 with standard `ErrorView` response.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, schema generation, final verification

- [ ] T027 [P] Add ArchUnit exclusion for `TransactionTimestampContext` and `TransactionTimestampAspect` in `ArchitectureTest` — these `@Component` classes intentionally skip `@LogExecution` to avoid noisy per-transaction logging
- [ ] T028 [P] Update `docs/configuration.md` with new configuration properties: `spring.jpa.properties.org.hibernate.envers.store_data_at_delete` and any other Envers-related properties added in T002
- [ ] T029 Run `./gradlew generateDbSchema` to regenerate `docs/db-schema.md` reflecting the 16 new audit tables from V1.55 migration
- [ ] T030 Run `./gradlew checkstyleMain checkstyleTest` and `./gradlew clean build` to verify all new code passes checkstyle, compiles without warnings (`-Werror`), and all tests pass across all database dialects

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user story phases
- **US1+US2 (Phase 3)**: Depends on Phase 2 completion
- **US3 (Phase 4)**: Depends on Phase 3 (extends the controller created in T025)
- **Polish (Phase 5)**: Depends on all prior phases

### Within Phases

- Phase 2 internal order: T003/T004 (parallel) → T005 (depends on T003) → T006-T010 (parallel) → T011 (depends on T006, T007) → T012-T014 (parallel, independent of T011) → T015 (after entities defined) → T016 (depends on T003, T008, T009, T011)
- Phase 3 internal order: T017-T018 (parallel) → T019 (depends on T017) → T020-T022 (parallel) → T023 (depends on T017) → T024 (depends on T018, T019, T020) → T025 (depends on T024, T023, T021, T022)
- Phase 4: T026 depends on T025
- Phase 5: T027, T028, and T029 are parallel; T030 runs last

### Parallel Opportunities

- **Phase 2**: T003+T004 parallel; T006+T007+T008+T009+T010 parallel; T012+T013+T014 parallel
- **Phase 3**: T017+T018 parallel; T020+T021+T022 parallel
- **Phase 5**: T027+T028+T029 parallel; T030 sequential last

---

## Parallel Example: Phase 2 Foundational

```
# Wave 1 — Transaction timestamp (parallel):
T003: Create TransactionTimestampContext
T004: Create TransactionTimestampAspect

# Wave 2 — Configuration (sequential, depends on T003):
T005: Update JpaConfiguration

# Wave 3 — Entities, enums, markers (parallel):
T006: Create ActivityType enum
T007: Create ActivityResourceType enum
T008: Create AuditRevisionEntity
T009: Create AuditActivityEntity
T010: Create package markers

# Wave 4 — Mapper + entity annotations (parallel):
T011: Create AuditActivityMapper (depends on T006, T007)
T012: Add @Audited to DeploymentEntity
T013: Add @Audited to ImageDefinitionEntity
T014: Add @Audited to DomainWhitelistEntity

# Wave 5 — Migration (depends on entity definitions):
T015: Create V1.55 Flyway migrations

# Wave 6 — Listener (depends on T003, T008, T009, T011):
T016: Create AuditRevisionListener
```

---

## Parallel Example: Phase 3 Activity List API

```
# Wave 1 — Domain model + repository (parallel):
T017: Create AuditActivity domain model
T018: Create AuditActivityJpaRepository

# Wave 2 — Mappers + DTOs (parallel):
T019: Create PersistenceAuditActivityMapper (depends on T017)
T020: Create PageRequestDto
T021: Create PageDto
T022: Create AuditActivityDto
T023: Create AuditActivityDtoMapper (depends on T017)

# Wave 3 — Service (depends on T018, T019, T020):
T024: Create AuditActivityService

# Wave 4 — Controller (depends on T024, T023, T021, T022):
T025: Create AuditActivityController
```

---

## Implementation Strategy

### MVP First (Phase 1 + 2 + 3)

1. Complete Phase 1: Setup — add dependencies and config
2. Complete Phase 2: Foundational — Envers infrastructure, all entities audited, listener active
3. Complete Phase 3: US1+US2 — Activity list API with filtering
4. **STOP and VALIDATE**: `./gradlew testFast` passes; create/update/delete entities and query `POST /api/v1/activities` to verify
5. Deploy/demo if ready — the core audit trail is fully functional

### Incremental Delivery

1. Phase 1 + 2 → Envers tracking active (audit tables populated, no API yet)
2. + Phase 3 → Activity list API live (MVP!)
3. + Phase 4 → Single activity detail endpoint
4. + Phase 5 → Docs updated, schema regenerated, full build verified

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- US4 (Track All Resources) and US5 (Historical Snapshots) are satisfied by Phase 2 Foundational infrastructure
- Tests are not included as they were not explicitly requested in the spec
- `@Audited` on base classes propagates to JOINED inheritance subtypes — no need to annotate 13 classes individually
- Flyway migration T015 produces 3 SQL files (one per dialect) with 16 tables each
- **Constitution deviation**: Activities endpoint uses custom `PageRequestDto` POST body instead of Spring `Pageable` parameter. Internally uses `PageRequest` (implements `Pageable`). Deliberate choice to match reference repo pattern (research.md Decision 4)
- **Access control**: Activity endpoints are accessible to all authenticated users (no `@FullAdminOnly`), matching reference implementation
- **ArchUnit exclusion**: `TransactionTimestampContext` and `TransactionTimestampAspect` skip `@LogExecution` to avoid noisy per-transaction logging; exclusion documented in `ArchitectureTest`
