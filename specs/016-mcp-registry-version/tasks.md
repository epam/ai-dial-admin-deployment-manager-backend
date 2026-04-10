# Tasks: Add Version to McpRegistryRef

**Input**: Design documents from `specs/016-mcp-registry-version/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: Test tasks included — the existing `ExternalRegistryRef` feature has functional tests that must be extended.

**Organization**: Tasks grouped by user story. US1 and US2 share the same code changes (adding a field to 3 records), so they are combined into a single foundational phase followed by story-specific test phases.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Record Changes)

**Purpose**: Add the `version` field to all three layers of the `McpRegistryRef` type. This is the single code change that enables all user stories.

**⚠️ CRITICAL**: All user story test work depends on this phase being complete.

- [x] T001 [P] Add optional `version` field to `McpRegistryRefDto` record in `src/main/java/com/epam/aidial/deployment/manager/web/dto/McpRegistryRefDto.java` — add `@Nullable @Pattern(regexp = ".*\\S.*", message = "must not be blank") String version` as second record component after `packageName`
- [x] T002 [P] Add optional `version` field to `McpRegistryRef` record in `src/main/java/com/epam/aidial/deployment/manager/model/McpRegistryRef.java` — add `@Nullable String version` as second record component after `packageName`
- [x] T003 [P] Add optional `version` field to `PersistenceMcpRegistryRef` record in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/PersistenceMcpRegistryRef.java` — add `String version` as second record component after `packageName`
- [x] T004 Run `./gradlew checkstyleMain` to verify code style compliance and `./gradlew compileJava compileTestJava` to verify compilation (MapStruct mapper generation) succeeds with the new field

**Checkpoint**: All three records have the `version` field. MapStruct auto-generates updated mapper code. No runtime changes yet — existing tests should still pass.

---

## Phase 2: User Story 1 — Specify a Version When Attaching an MCP Registry Reference (Priority: P1) 🎯 MVP

**Goal**: Operators can create/update image definitions and deployments with a versioned `McpRegistryRef`.

**Independent Test**: Create an image definition with a `McpRegistryRef` including `packageName` and `version`. Retrieve it. Assert both fields are returned.

### Tests for User Story 1

- [x] T005 [US1] Add test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ImageDefinitionFunctionalTest.java` — test creating an image definition with `McpRegistryRef` that includes both `packageName` and `version`; assert the response returns both fields
- [x] T006 [US1] Add test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ImageDefinitionFunctionalTest.java` — test creating an image definition with `McpRegistryRef` that includes only `packageName` (no `version`); assert `version` is absent/null in response
- [x] T007 [US1] Add test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/DeploymentFunctionalTest.java` — test creating a deployment with `ImageReferenceSource` carrying a `McpRegistryRef` with `packageName` and `version`; assert both fields returned
- [x] T008 [US1] Add validation test — test that sending `McpRegistryRef` with blank `version` (empty string or whitespace) returns HTTP 400

**Checkpoint**: User Story 1 tests pass. Versioned `McpRegistryRef` can be written and read via API.

---

## Phase 3: User Story 2 — Read Version from Existing MCP Registry References (Priority: P1)

**Goal**: Clients reading image definitions and deployments see the `version` field when present, and it is absent for legacy records.

**Independent Test**: Seed records with and without `version`. Fetch via list and single-get endpoints. Assert correct presence/absence.

### Tests for User Story 2

- [x] T009 [US2] Add test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ImageDefinitionFunctionalTest.java` — test that listing image definitions returns `version` for records that have it and omits it for records that don't
- [x] T010 [US2] Add backward-compatibility test — test that an image definition created without `version` in its `McpRegistryRef` can be read without errors and `version` is null

**Checkpoint**: Read path verified for both versioned and non-versioned `McpRegistryRef` records.

---

## Phase 4: User Story 3 — Clear or Update Version on an MCP Registry Reference (Priority: P2)

**Goal**: Operators can change or remove the `version` from an existing `McpRegistryRef`.

**Independent Test**: Create a record with version. Update to different version. Assert new version. Update without version. Assert version absent.

### Tests for User Story 3

- [x] T011 [US3] Add test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ImageDefinitionFunctionalTest.java` — test updating an image definition's `McpRegistryRef` from `version: "1.0.0"` to `version: "2.0.0"`; assert new version returned
- [x] T012 [US3] Add test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ImageDefinitionFunctionalTest.java` — test clearing `version` by sending `McpRegistryRef` with only `packageName`; assert `version` is absent in response

**Checkpoint**: Full CRUD lifecycle for the `version` field verified.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Export/import verification and final validation.

- [x] T013 [P] Add export/import test in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ConfigExportImportFunctionalTest.java` — test that a record with versioned `McpRegistryRef` survives export and import round-trip with `version` intact
- [x] T014 Run `./gradlew checkstyleMain checkstyleTest` to verify full code style compliance
- [x] T015 Run `./gradlew testFast` to verify all tests pass (H2)
- [x] T016 Run `./gradlew clean build` for full build validation including all testcontainers tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Foundational)**: No dependencies — start immediately
- **Phase 2 (US1)**: Depends on Phase 1 completion
- **Phase 3 (US2)**: Depends on Phase 1 completion (can run in parallel with Phase 2)
- **Phase 4 (US3)**: Depends on Phase 1 completion (can run in parallel with Phases 2-3)
- **Phase 5 (Polish)**: Depends on all user story phases being complete

### User Story Dependencies

- **User Story 1 (P1)**: Depends only on Phase 1 — no dependencies on other stories
- **User Story 2 (P1)**: Depends only on Phase 1 — can run in parallel with US1
- **User Story 3 (P2)**: Depends only on Phase 1 — can run in parallel with US1/US2

### Within Phase 1

- T001, T002, T003 can all run in parallel (different files)
- T004 depends on T001-T003 completion

### Parallel Opportunities

- T001, T002, T003: All modify different files — fully parallelizable
- T005-T008 (US1 tests) and T009-T010 (US2 tests) touch different test methods — could run in parallel after Phase 1
- T013 (export/import) is independent of US-specific tests

---

## Parallel Example: Phase 1

```bash
# Launch all record changes together (different files):
Task T001: "Add version to McpRegistryRefDto"
Task T002: "Add version to McpRegistryRef"
Task T003: "Add version to PersistenceMcpRegistryRef"
# Then verify:
Task T004: "Run checkstyle + compile"
```

---

## Implementation Strategy

### MVP First (Phase 1 + US1)

1. Complete Phase 1: Add `version` field to 3 records
2. Complete Phase 2: US1 tests (write + read with version)
3. **STOP and VALIDATE**: `./gradlew testFast` — MVP is working
4. Proceed to US2, US3, Polish

### Incremental Delivery

1. Phase 1 → Records updated, compiles clean
2. + Phase 2 (US1) → Versioned write/read works → MVP!
3. + Phase 3 (US2) → Read path verified for mixed records
4. + Phase 4 (US3) → Update/clear lifecycle verified
5. + Phase 5 → Export/import + full build green

---

## Notes

- No MapStruct mapper changes needed — auto-mapped by field name
- No database migration needed — JSON column handles new field naturally
- No controller or service changes — records are the only source code modified
- Existing tests should continue to pass after Phase 1 (backward compatible)
- Commit after each phase for clean history
