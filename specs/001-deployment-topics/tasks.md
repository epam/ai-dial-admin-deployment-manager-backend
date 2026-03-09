# Tasks: Deployment Topics

**Input**: Design documents from `/specs/001-deployment-topics/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Database + Model Layer)

**Purpose**: Schema migration and model changes that ALL user stories depend on. Once complete, US1-US3 (Create, Update, View) are satisfied — topics flow through existing CRUD endpoints via MapStruct auto-mapping.

- [x] T001 [P] Create Flyway migration `src/main/resources/db/migration/H2/V1.47__CreateDeploymentTopicsTable.sql` — create `deployment_topics` table with columns `deployment_id VARCHAR(36) NOT NULL` and `topic_name VARCHAR(255) NOT NULL`, composite PK (deployment_id, topic_name), FK to `deployment(id)` with cascade delete
- [x] T002 [P] Create Flyway migration `src/main/resources/db/migration/POSTGRES/V1.47__CreateDeploymentTopicsTable.sql` — identical SQL as H2 (no vendor-specific types needed for this table)
- [x] T003 [P] Create Flyway migration `src/main/resources/db/migration/MS_SQL_SERVER/V1.47__CreateDeploymentTopicsTable.sql` — identical SQL as H2 (no NVARCHAR/UNIQUEIDENTIFIER needed — only VARCHAR columns)
- [x] T004 [P] Add `topics` field with `@ElementCollection` + `@CollectionTable(name = "deployment_topics")` to `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/DeploymentEntity.java`
- [x] T005 [P] Add `private List<String> topics` field to `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Deployment.java`
- [x] T006 [P] Add `private List<String> topics` field to `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateDeployment.java`
- [x] T007 [P] Add `@Nullable @ValidTopics private List<String> topics` field to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateDeploymentRequestDto.java`
- [x] T008 [P] Add `@Nullable private List<String> topics` field to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/DeploymentDto.java`
- [x] T009 [P] Add `@Nullable List<String> topics` parameter to `src/main/java/com/epam/aidial/deployment/manager/web/dto/DeploymentInfoDto.java` record
- [x] T010 Add `existingEntity.setTopics(updatedEntity.getTopics())` to `updateEntityFromDomain()` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java`

**Checkpoint**: After Phase 1, deployments support topics on create, update, and view (US1+US2+US3). MapStruct auto-maps `topics` across all mapper layers. Run `./gradlew clean build` to verify compilation and existing tests pass.

---

## Phase 2: User Story 4 — Topics Listing Includes Deployment Topics (Priority: P2)

**Goal**: The `GET /api/v1/topics` endpoint returns a unified, deduplicated list of topics from both image definitions and deployments.

**Independent Test**: Create a deployment with a unique topic not on any image definition; verify it appears in the topics listing.

- [x] T011 [US4] Replace JPQL query with native SQL UNION in `src/main/java/com/epam/aidial/deployment/manager/dao/repository/TopicRepository.java` — `SELECT DISTINCT topic_name FROM (SELECT topic_name FROM image_definition_topics UNION SELECT topic_name FROM deployment_topics) AS all_topics ORDER BY topic_name`

**Checkpoint**: Topics listing now includes deployment topics, deduplicated and sorted.

---

## Phase 3: Verification (US5 + US6 — Automatic via Existing Patterns)

**Purpose**: US5 (Duplication) and US6 (Export/Import) require NO additional code changes — they work automatically through existing MapStruct mappers and Jackson serialization. This phase is verification-only.

- [x] T012 [US5] Verify duplication copies topics — `DeploymentMapper.toCreateCloneDeployment()` in `src/main/java/com/epam/aidial/deployment/manager/mapper/DeploymentMapper.java` auto-maps `topics` via `toCreateDeployment()`. No code change needed; verify by reading generated MapStruct implementation after build.
- [x] T013 [US6] Verify export includes topics — confirm `topics` is NOT listed in `src/main/java/com/epam/aidial/deployment/manager/configuration/export/DeploymentExportMixIn.java` exclusions. No code change needed.

**Checkpoint**: All 6 user stories are satisfied.

---

## Phase 4: Functional Tests

**Purpose**: Add functional test coverage for deployment topics across all supported DB vendors.

- [x] T014 [P] Add deployment topics functional tests to `src/test/java/com/epam/aidial/deployment/manager/functional/tests/TopicFunctionalTest.java` — test create with topics, create without topics, update topics, topics listing includes deployment topics, topics deduplication, invalid topics rejected (blank, >255 chars, whitespace)
- [x] T015 [P] [US6] Add deployment topics to config export/import functional tests in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ConfigExportImportFunctionalTest.java` — test that exported deployment includes topics and re-imported deployment preserves them
- [x] T016 Verify all functional tests pass on H2 by running `./gradlew test`

**Checkpoint**: Full test coverage. Run `./gradlew clean build` for final validation.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Foundational)**: No dependencies — all tasks T001-T009 can run in parallel; T010 depends on T004+T005
- **Phase 2 (US4)**: Depends on T001-T003 (migration must exist for native query)
- **Phase 3 (Verification)**: Depends on Phase 1 completion
- **Phase 4 (Tests)**: Depends on all prior phases

### User Story Dependencies

- **US1-US3 (P1)**: Satisfied by Phase 1 — no inter-story dependencies
- **US4 (P2)**: Requires Phase 1 (migration) + T011
- **US5 (P2)**: Satisfied automatically by Phase 1 — verify only
- **US6 (P2)**: Satisfied automatically by Phase 1 — verify only

### Parallel Opportunities

- T001-T009 are all independent file changes — maximum parallelism
- T011 can start as soon as migrations (T001-T003) are done
- T012-T013 are read-only verification — can run in parallel

---

## Parallel Example: Phase 1

```
# All these modify different files and can run simultaneously:
T001: Migration H2
T002: Migration POSTGRES
T003: Migration MS_SQL_SERVER
T004: DeploymentEntity.java
T005: Deployment.java
T006: CreateDeployment.java
T007: CreateDeploymentRequestDto.java
T008: DeploymentDto.java
T009: DeploymentInfoDto.java
# Then sequentially:
T010: PersistenceDeploymentMapper.java (depends on T004, T005)
```

---

## Implementation Strategy

### MVP (Phase 1 Only)

1. Complete Phase 1 (T001-T010)
2. Run `./gradlew clean build` — validates compilation, Checkstyle, existing tests
3. Deployments now support topics on create/update/view

### Full Feature

1. Phase 1 → MVP ready (US1+US2+US3)
2. Phase 2 → Topics listing unified (US4)
3. Phase 3 → Verify duplication + export/import (US5+US6)
4. Phase 4 → Functional test coverage
5. Final: `./gradlew clean build` passes

---

## Notes

- This is a small, additive feature — most work is field propagation across layers
- MapStruct auto-maps `topics` (same name + type at every layer) — no explicit `@Mapping` needed
- Export/import and duplication work automatically — no code changes, just verification
- The only non-trivial logic change is the TopicRepository UNION query (T011)
