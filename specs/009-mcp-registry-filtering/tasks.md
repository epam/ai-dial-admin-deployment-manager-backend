# Tasks: MCP Registry Backend Filtering

**Input**: Design documents from `/specs/009-mcp-registry-filtering/`
**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, contracts/api.md ✅, research.md ✅

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to
- Include exact file paths in descriptions

## Path Conventions

Single-project layout: `src/main/java/…`, `src/test/java/…` at repository root.
Base package: `com.epam.aidial.deployment.manager.registry.mcp`

---

## Phase 1: Setup (Shared Infrastructure)

No setup required — this is a modification to an existing Spring Boot project with all infrastructure in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Rename `remoteTypes` → `remoteTransportTypes` and add `packageTransportTypes` in `ServerFilterDto`. This is the single DTO change that all downstream source and test files depend on.

**⚠️ CRITICAL**: Tasks T002 and T003 cannot start until T001 is complete.

- [x] T001 Rename field `remoteTypes` → `remoteTransportTypes` (update field name, Lombok builder key, and Javadoc) and add new field `@Nullable private List<String> packageTransportTypes` with Javadoc: "Package transport types to match (OR logic). Values: stdio, streamable-http, sse. Matched case-insensitively against Package.transport.type. A package with null transport does not match." in `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/web/dto/ServerFilterDto.java`

**Checkpoint**: `ServerFilterDto` compiles — all downstream tasks can begin.

---

## Phase 3: User Story 1 — Filter MCP Servers by Properties (Priority: P1) 🎯 MVP

**Goal**: `McpServerFilter` evaluates the new `packageTransportTypes` dimension; `McpRegistryController` exposes the renamed `remoteTransportTypes` and new `packageTransportTypes` GET query params. Both endpoints (GET and POST) accept the updated filter shape.

**Independent Test**: `GET /api/v1/mcp-registry/servers?packageTransportTypes=stdio` returns only servers that have at least one package with `transport.type = "stdio"`. `GET /api/v1/mcp-registry/servers?remoteTransportTypes=sse` still works after the rename (old `remoteTypes` param no longer accepted).

### Implementation for User Story 1

- [x] T002 [P] [US1] Add private method `matchesPackageTransportTypes(List<Package> packages, List<String> filterTypes)` — returns `false` when packages is null/empty; iterates packages and returns `true` if any package has non-null `transport`, non-null `transport.type`, and `transport.type.getValue().equalsIgnoreCase(filterType)` for any filter value; update `matches()` to call it when `filter.getPackageTransportTypes()` is non-empty; update `hasActiveCriteria()` to also return `true` when `packageTransportTypes` is non-empty; rename `getRemoteTypes()` → `getRemoteTransportTypes()` references in existing `matchesRemoteTypes` call in `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/service/McpServerFilter.java`
- [x] T003 [P] [US1] Rename `@RequestParam List<String> remoteTypes` → `remoteTransportTypes` with updated `@Parameter(description = "Remote transport types to filter by (OR logic). Renamed from remoteTypes. Values: sse, streamable-http")`; add `@Parameter(description = "Package transport types to filter by (OR logic). Values: stdio, streamable-http, sse") @RequestParam(required = false) List<String> packageTransportTypes`; update `buildFilter()` method signature to accept `remoteTransportTypes` and `packageTransportTypes`; update `buildFilter()` null-check to include `packageTransportTypes`; update `ServerFilterDto.builder()` to use `.remoteTransportTypes(remoteTransportTypes)` and `.packageTransportTypes(packageTransportTypes)` in `src/main/java/com/epam/aidial/deployment/manager/registry/mcp/web/controller/McpRegistryController.java`

**Checkpoint**: US1 is fully functional — new filter dimension wired through DTO → predicate → controller.

---

## Phase 4: User Story 2 — Combine Multiple Filters (Priority: P1)

**Goal**: Verify AND-across-dimensions logic correctly applies `packageTransportTypes` alongside the existing three filter dimensions. No new source code required — this phase covers test tasks only, since the AND logic already works for any filter field added to `McpServerFilter`.

**Independent Test**: `GET /api/v1/mcp-registry/servers?remoteTransportTypes=sse&packageRegistryTypes=npm&packageTransportTypes=stdio` returns only servers satisfying all three criteria simultaneously.

### Tests for User Story 2

- [x] T004 [P] [US2] In `McpServerFilterTest`: (a) rename all `.remoteTypes(...)` builder calls → `.remoteTransportTypes(...)`; (b) add helper `pkgWithTransport(LocalTransportType type)` returning `Package.builder().registryType(PackageRegistryType.NPM).transport(LocalTransport.builder().type(type).build()).build()`; (c) add test cases: `shouldMatchSinglePackageTransportType`, `shouldMatchPackageTransportTypeCaseInsensitive` (filter "STDIO", transport `LocalTransportType.STDIO`), `shouldMatchMultiValuePackageTransportTypeOrLogic` (filter ["stdio","sse"], server has stdio), `shouldNotMatchPackageTransportType_whenNoMatch`, `shouldNotMatchPackageTransportType_whenPackagesNull`, `shouldNotMatchPackageTransportType_whenPackagesEmpty`, `shouldNotMatchPackageTransportType_whenTransportIsNull` (package with `transport = null`); (d) update `shouldMatchAll_whenFilterHasEmptyLists` and `shouldReturnFalse_whenAllCriteriaEmpty` to include `packageTransportTypes(Collections.emptyList())`; (e) add `shouldReturnTrue_whenPackageTransportTypesPresent`; (f) add combined AND test `shouldMatchCombinedFilters_remoteTransportAndPackageTransportTypes` (server with SSE remote AND stdio-transport package); (g) add independent cross-package test `shouldMatchCombinedFilters_packageRegistryAndPackageTransportTypes_independentEvaluation` (OCI package + different package with streamable-http transport) in `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/service/McpServerFilterTest.java`
- [x] T005 [P] [US2] In `McpRegistryControllerTest`: (a) rename all `.param("remoteTypes", ...)` → `.param("remoteTransportTypes", ...)`; (b) update all captured `ServerFilterDto` assertions from `getRemoteTypes()` → `getRemoteTransportTypes()`; (c) add test `getServers_shouldPassPackageTransportTypesFilter_whenParamProvided` — performs `GET /api/v1/mcp-registry/servers` with `.param("packageTransportTypes", "stdio")`, captures the `ServersRequestDto` argument passed to `mcpRegistryService.getServers()`, asserts `filter.getPackageTransportTypes()` equals `List.of("stdio")`; (d) verify `buildFilter` returns `null` when `remoteTransportTypes`, `packageRegistryTypes`, `packageTransportTypes`, and `repositoryExists` are all absent in `src/test/java/com/epam/aidial/deployment/manager/mcpregistry/web/controller/McpRegistryControllerTest.java`

**Checkpoint**: US1 and US2 fully covered — all renamed references updated, all new test cases pass.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Documentation accuracy, code style, and full test suite validation.

- [x] T006 [P] Verify `docs/configuration.md` has an accurate entry for `MCP_REGISTRY_MAX_PAGES_TO_SCAN` / `app.mcp-registry.max-pages-to-scan` with default value `25` — add or update if missing or stale in `docs/configuration.md`
- [x] T007 Run `./gradlew checkstyleMain checkstyleTest` and fix any Google Java Style / 180-char line violations introduced by T001–T003
- [x] T008 Run `./gradlew testFast` and confirm all tests pass (depends on T004, T005, T007)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1–2 (Setup + Foundational)**: Start immediately; T001 must complete before T002/T003
- **Phase 3 (US1)**: T002 and T003 in parallel after T001
- **Phase 4 (US2 Tests)**: T004 depends on T002; T005 depends on T003; T004 and T005 run in parallel
- **Phase 5 (Polish)**: T006 is independent; T007 after T001–T003; T008 after T004, T005, T007

### User Story Coverage

| Story | Priority | Status | Tasks |
|-------|----------|--------|-------|
| US1 — Filter by properties | P1 | New work | T001, T002, T003 |
| US2 — Combine multiple filters | P1 | Tested by Phase 4 | T004, T005 |
| US3 — Paginate filtered results | P2 | Already implemented | — |
| US4 — Configurable scan limit | P3 | Already implemented | — |

### Parallel Opportunities

- **T002 and T003** run in parallel after T001 (different files: service vs. controller)
- **T004 and T005** run in parallel (different test files)
- **T006** runs at any time (independent docs update)

---

## Parallel Example: Phase 3

```
# After T001 (ServerFilterDto) compiles:
Task T002: McpServerFilter.java — add packageTransportTypes logic, rename remoteTypes ref
Task T003: McpRegistryController.java — rename remoteTypes param, add packageTransportTypes param
```

## Parallel Example: Phase 4

```
# After T001 (and T002 for T004, T003 for T005):
Task T004: McpServerFilterTest.java — rename refs, add packageTransportTypes test cases
Task T005: McpRegistryControllerTest.java — rename refs, add packageTransportTypes param test
```

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Complete Phase 2: T001 — DTO rename + new field
2. Complete Phase 3: T002 + T003 in parallel — filter logic and controller
3. **STOP and VALIDATE**: `GET /api/v1/mcp-registry/servers?packageTransportTypes=stdio` works; `remoteTransportTypes` param accepted; POST body `filter.packageTransportTypes` passes through
4. Continue with Phase 4 (tests) + Phase 5 (polish)

### Incremental Delivery

US3 (pagination) and US4 (configurable limit) are already fully implemented. This feature is a single cohesive increment: rename + add one filter dimension. Deliver as one PR.

---

## Notes

- [P] tasks = different files, no dependency on an incomplete task in the same phase
- US3 and US4 are already implemented and tested — `McpRegistryService` and `McpRegistryProperties` need no changes
- The rename `remoteTypes` → `remoteTransportTypes` is a **breaking change** for any existing API callers using the old GET query param — note in release notes/changelog
- `packageTransportTypes` and `packageRegistryTypes` use independent OR evaluation across all packages on a server (no co-location requirement — confirmed clarification 2026-04-02)
- Null transport on a `Package` means that package does not match `packageTransportTypes` and is simply skipped; the server matches only if another of its packages has a non-null matching transport
