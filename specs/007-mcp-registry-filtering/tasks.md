# Tasks: MCP Registry Backend Filtering

**Input**: Design documents from `/specs/007-mcp-registry-filtering/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/api.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create shared DTOs, configuration, and test fixtures that all user stories depend on

- [ ] T001 Create `ServerFilterDto` DTO with `remoteTypes` (List\<String\>), `packageRegistryTypes` (List\<String\>), and `repositoryExists` (Boolean) fields in `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/web/dto/ServerFilterDto.java`
- [ ] T002 [P] Add `filter` field (type `ServerFilterDto`, nullable) to `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/web/dto/ServersRequestDto.java`
- [ ] T003 [P] Add `maxPagesToScan` field (int, default 5) to `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/properties/McpRegistryProperties.java` and add `max-pages-to-scan: ${MCP_REGISTRY_MAX_PAGES_TO_SCAN:5}` to `src/main/resources/application.yml` under `app.mcp-registry`
- [ ] T004 [P] Create test JSON fixtures: `src/test/resources/mcp-registry/servers_page_mixed.json` (servers with diverse remotes, packages, repositories), `src/test/resources/mcp-registry/servers_page_empty.json` (empty server list with no nextCursor)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Filter predicate component — MUST be complete before any user story implementation

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 Create `McpServerFilter` Spring component with `matches(ServerResponseDto, ServerFilterDto)` method implementing: remote type matching (OR within list, case-insensitive against `server.getServer().getRemotes()[].type`), package registry type matching (OR within list, case-insensitive against `server.getServer().getPackages()[].registryType.getValue()`), repository existence check (null check on `server.getServer().getRepository()`). Use `CollectionUtils.isEmpty()` for null-safe collection checks. Add `@LogExecution` and `@Component` annotations. File: `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/service/McpServerFilter.java`
- [ ] T006 Create unit tests for `McpServerFilter` covering: single remote type match, multi-value remote type OR logic, single package type match, multi-value package type OR logic, repository exists=true, repository exists=false, null/empty remotes with remote filter (should not match), null/empty packages with package filter (should not match), null filter (should match all), empty filter lists (should match all). File: `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/service/McpServerFilterTest.java`

**Checkpoint**: Filter predicate is ready and tested — user story implementation can now begin

---

## Phase 3: User Story 1+2 — Filter & Combine MCP Servers (Priority: P1) 🎯 MVP

**Goal**: Administrators can filter MCP servers by remote type, package type, and repository existence. Multiple filters combine with AND logic across dimensions, OR logic within a dimension's value list.

**Independent Test**: Send a list-servers request with filter params (e.g., `remoteTypes=sse&packageRegistryTypes=npm&repositoryExists=true`) and verify only matching servers are returned.

### Implementation

- [ ] T007 [US1] [US2] Implement multi-page scanning loop in `McpRegistryService.getServers()`: when `ServerFilterDto` has active criteria, iterate upstream pages using `McpRegistryClient.getServers()`, apply `McpServerFilter.matches()` to each server, accumulate results until page size is filled or scan limit (`McpRegistryProperties.maxPagesToScan`) is reached or upstream exhausted. When no filter is active, preserve existing single-request pass-through behavior. Handle upstream errors mid-scan: if `McpRegistryClientException` is thrown and results have already been collected, return the partial results with the last successful cursor; if no results collected yet, propagate the exception. Upstream-supported filters (`search`, `updatedSince`, `version`) MUST remain on the request forwarded to the upstream registry alongside backend filtering. File: `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/service/McpRegistryService.java`
- [ ] T008 [US1] [US2] Update GET endpoint in `McpRegistryController.getServers()` to accept new query params: `remoteTypes` (List\<String\>), `packageRegistryTypes` (List\<String\>), `repositoryExists` (Boolean). Construct `ServerFilterDto` from these params and set it on the `ServersRequestDto`. File: `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/web/controller/McpRegistryController.java`
- [ ] T009 [US1] [US2] Create unit tests for `McpRegistryService` scanning loop covering: single filter dimension returns only matching servers, multiple filter dimensions use AND logic, multi-value filter uses OR within dimension, no filter preserves pass-through (single upstream call), upstream returns mixed servers and only matching ones are collected, first page has no matches but subsequent pages do — loop continues, error mid-scan with partial results returns collected results, error mid-scan with no results propagates exception, upstream `search` param is still forwarded when backend filter is also active. Mock `McpRegistryClient` and `McpServerFilter`. File: `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/service/McpRegistryServiceTest.java`
- [ ] T010 [US1] [US2] Add controller tests: GET with `remoteTypes` param passes filter to service, GET with `packageRegistryTypes` param passes filter to service, GET with `repositoryExists` param passes filter to service, GET with multiple filter params combines them, GET with no filter params results in null filter (pass-through), POST with `filter` object in JSON body passes filter to service. File: `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/web/controller/McpRegistryControllerTest.java`

**Checkpoint**: Filtering and combining works for both GET and POST endpoints. MVP is functional.

---

## Phase 4: User Story 3 — Paginate Through Filtered Results (Priority: P2)

**Goal**: Callers can paginate through filtered results using `nextCursor`, identically to non-filtered pagination. Scan limit hit returns a cursor; upstream exhausted returns null cursor.

**Independent Test**: Request first page of filtered servers, use returned cursor for next page, verify no duplicates and proper continuation.

**Depends on**: Phase 3 (scanning loop must exist)

### Implementation

- [ ] T011 [US3] Add pagination-specific test scenarios to `McpRegistryServiceTest`: scan limit reached with upstream remaining returns nextCursor for continuation, caller provides cursor and scanning resumes from correct upstream position, upstream exhausted returns null nextCursor, partial page returned when scan limit hit (fewer results than limit), second request with cursor returns non-overlapping results. File: `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/service/McpRegistryServiceTest.java`
- [ ] T012 [US3] Add pagination controller test scenarios: GET with cursor param and filter params passes both to service, response metadata includes nextCursor when more results exist, response metadata has null nextCursor when upstream exhausted. File: `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/web/controller/McpRegistryControllerTest.java`

**Checkpoint**: Paginated browsing through filtered results works seamlessly

---

## Phase 5: User Story 4 — Configurable Maximum Page Scan Limit (Priority: P3)

**Goal**: Operators can control how many upstream pages the system scans per request via the `MCP_REGISTRY_MAX_PAGES_TO_SCAN` environment variable.

**Independent Test**: Configure a low scan limit (e.g., 2), apply a rare filter, verify the system stops after the configured number of pages.

**Depends on**: Phase 3 (scanning loop must exist)

### Implementation

- [ ] T013 [US4] Add scan-limit-specific test scenarios to `McpRegistryServiceTest`: configured limit of 2 stops after 2 upstream pages regardless of results collected, default limit (5) is used when not explicitly configured, scan limit of 1 fetches exactly one upstream page. File: `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/service/McpRegistryServiceTest.java`

**Checkpoint**: Scan limit is configurable and bounded

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, API annotations, and quality verification

- [ ] T014 [P] Add OpenAPI `@Operation` and `@Parameter` annotations for new filter query params (`remoteTypes`, `packageRegistryTypes`, `repositoryExists`) on the GET endpoint in `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/web/controller/McpRegistryController.java`
- [ ] T015 [P] Update `docs/configuration.md` — add row for `app.mcp-registry.max-pages-to-scan` / `MCP_REGISTRY_MAX_PAGES_TO_SCAN` with default `5` and description
- [ ] T016 Run `./gradlew checkstyleMain checkstyleTest` and fix any violations
- [ ] T017 Run `./gradlew testFast` to verify all tests pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on T001 (ServerFilterDto) from Setup — BLOCKS all user stories
- **US1+US2 (Phase 3)**: Depends on Phase 2 completion (McpServerFilter ready)
- **US3 (Phase 4)**: Depends on Phase 3 (scanning loop must exist to test pagination)
- **US4 (Phase 5)**: Depends on Phase 3 (scanning loop must exist to test scan limits)
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **US1+US2 (P1)**: Can start after Foundational (Phase 2) — no dependencies on other stories
- **US3 (P2)**: Depends on US1+US2 — pagination is a property of the scanning loop
- **US4 (P3)**: Depends on US1+US2 — scan limit is a property of the scanning loop
- **US3 and US4**: Can run in parallel with each other (both only add tests to existing code)

### Within Each Phase

- DTOs before services
- Services before controllers
- Implementation before tests (tests validate the implementation)
- Core logic before integration tests

### Parallel Opportunities

- T002, T003, T004 can all run in parallel (different files, no dependencies)
- T014, T015 can run in parallel (different files)
- Phase 4 (US3) and Phase 5 (US4) can run in parallel (both add tests to existing code)

---

## Parallel Example: Phase 1 Setup

```
# These three tasks can run in parallel after T001:
Task T002: Add filter field to ServersRequestDto.java
Task T003: Add maxPagesToScan to McpRegistryProperties.java + application.yml
Task T004: Create test JSON fixtures
```

## Parallel Example: Phase 3 US1+US2

```
# After T007 (service) is complete:
Task T008: Update controller (different file from T007)
Task T009: Create service unit tests (test file, different from T007)
# After T008:
Task T010: Create controller tests
```

---

## Implementation Strategy

### MVP First (User Story 1+2 Only)

1. Complete Phase 1: Setup (DTOs, config, fixtures)
2. Complete Phase 2: Foundational (McpServerFilter)
3. Complete Phase 3: US1+US2 (scanning loop, controller, tests)
4. **STOP and VALIDATE**: Test filtering via GET and POST endpoints independently
5. Deploy/demo if ready — filtering and combining work end-to-end

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1+US2 → Test filtering and combining → Deploy (MVP!)
3. Add US3 → Test pagination through filtered results → Deploy
4. Add US4 → Test configurable scan limit → Deploy
5. Polish → OpenAPI docs, configuration docs, checkstyle → Final release

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US1 and US2 are combined in Phase 3 because the filter model inherently supports both single and combined filters — they cannot be implemented separately
- US3 (pagination) and US4 (scan limit) are testing phases — the underlying code is already part of the scanning loop in Phase 3
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
