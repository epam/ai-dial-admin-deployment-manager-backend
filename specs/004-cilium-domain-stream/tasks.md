# Tasks: Cilium Domain Access Streaming

**Input**: Design documents from `/specs/004-cilium-domain-stream/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Test tasks are included in Polish phase; extend existing ImageBuildLogsServiceTest and ImageBuildControllerTest per quickstart.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Repository root**: `src/main/java/com/epam/aidial/deployment/manager/`, `src/main/resources/db/migration/`
- Paths below are relative to repo root unless stated otherwise

---

## Phase 1: Setup (Proto and codegen)

**Purpose**: Hubble gRPC protobuf and Java code generation (research.md)

- [x] T001 Add Gradle protobuf plugin and configure Cilium proto source and Java/gRPC codegen output in `build.gradle`
- [x] T002 [P] Add Cilium proto files (observer.proto, flow.proto, relay.proto and dependencies) under `src/main/proto` with package layout `api/v1/observer/`, `api/v1/flow/`, `api/v1/relay/` per research.md

---

## Phase 2: Foundational (Blocking prerequisites)

**Purpose**: Data model, persistence, and service layer for accessed domains. MUST be complete before user story implementation.

**Critical**: No user story work can begin until this phase is complete.

- [x] T003 Add Flyway V1.50 migration adding `accessed_domains` column (JSON, default `[]`) to `image_definition` in `src/main/resources/db/migration/H2/V1.50__AddAccessedDomainsToImageDefinition.sql`
- [x] T004 Add Flyway V1.50 migration adding `accessed_domains` column (JSONB, default `[]`) to `image_definition` in `src/main/resources/db/migration/POSTGRES/V1.50__AddAccessedDomainsToImageDefinition.sql`
- [x] T005 Add Flyway V1.50 migration adding `accessed_domains` column (NVARCHAR(MAX), default `[]`) to `image_definition` in `src/main/resources/db/migration/MS_SQL_SERVER/V1.50__AddAccessedDomainsToImageDefinition.sql`
- [x] T006 [P] Create AccessVerdict enum (ALLOWED, BLOCKED) in `src/main/java/com/epam/aidial/deployment/manager/model/AccessVerdict.java`
- [x] T007 [P] Create AccessedDomain model (domain: String, verdict: AccessVerdict) in `src/main/java/com/epam/aidial/deployment/manager/model/AccessedDomain.java`
- [x] T008 Add `accessedDomains` field (List&lt;AccessedDomain&gt;) to ImageDefinition in `src/main/java/com/epam/aidial/deployment/manager/model/ImageDefinition.java`
- [x] T009 Add `accessedDomains` field (List&lt;AccessedDomain&gt;, JSON column `accessed_domains`) to ImageDefinitionEntity in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/ImageDefinitionEntity.java`
- [x] T010 Update PersistenceImageDefinitionMapper to map `List<AccessedDomain>` to/from persistence (and any persistence-specific type) in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/`
- [x] T011 Add `addAccessedDomains(UUID id, List<AccessedDomain>)` and `resetAccessedDomains(UUID id)` to ImageDefinitionRepository in `src/main/java/com/epam/aidial/deployment/manager/dao/repository/ImageDefinitionRepository.java` (merge logic: one entry per distinct domain, verdict = allowed if any allowed else blocked)
- [x] T012 Add `addAccessedDomain(UUID id, String domain, AccessVerdict verdict)`, `addAccessedDomains(UUID id, List<AccessedDomain>)`, and `resetAccessedDomains(UUID id)` to ImageDefinitionService in `src/main/java/com/epam/aidial/deployment/manager/service/ImageDefinitionService.java`; ensure build start path calls `resetAccessedDomains` (e.g. where `resetBuildLogs` is called in ImageBuildRunner or pipeline)
- [x] T013 [P] Create AccessedDomainDto (or record with domain, verdict) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/` and add `accessedDomains` field to ImageBuildDetailsDto in `src/main/java/com/epam/aidial/deployment/manager/web/dto/ImageBuildDetailsDto.java`
- [x] T014 Update ImageBuildDetailsDtoMapper to map `accessedDomains` from ImageDefinition to DTO in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ImageBuildDetailsDtoMapper.java`

**Checkpoint**: Foundation ready — storage, service, and details DTO support accessed domains; user story implementation can begin.

---

## Phase 3: User Story 1 — View real-time domain access stream (Priority: P1) — MVP

**Goal**: Administrator can open a dedicated SSE stream for domain access during an image build, separate from build logs; entries appear as the build runs; replay on reconnect; stream completes on final build status; zero-domains indication when build ends with no domains (FR-009).

**Independent Test**: Start an image build with Cilium enabled, open `GET /api/v1/images/builds/{id}/accessed-domains`, verify domain entries (or zero-domains event) and status events; open logs stream in parallel and confirm streams do not interleave.

- [x] T015 [US1] Implement `streamAccessedDomains(UUID imageDefinitionId)` in ImageBuildLogsService in `src/main/java/com/epam/aidial/deployment/manager/service/ImageBuildLogsService.java`: poll imageDefinition.getAccessedDomains(), send SSE events `accessed-domains` (per entry) and `status`, support replay of already-persisted entries on reconnect, complete emitter on final build status, send explicit no-domain-access indication when build ends with zero domains (FR-009)
- [x] T016 [US1] Add `GET /api/v1/images/builds/{id}/accessed-domains` (produces `text/event-stream`) and method `subscribeToAccessedDomains(@PathVariable UUID id)` delegating to `imageBuildLogsService.streamAccessedDomains(id)` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ImageBuildController.java`
- [x] T017 [US1] Ensure GET details endpoint returns `accessedDomains` in response (ImageBuildController getImageBuildLogsById uses extended ImageBuildDetailsDto; verify mapper and DTO are wired) in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ImageBuildController.java`

**Checkpoint**: User Story 1 is complete; stream endpoint and details DTO deliver domain entries with domain and verdict.

---

## Phase 4: User Story 2 — See allowed or blocked per domain (Priority: P2)

**Goal**: Each domain entry in the stream and in details clearly indicates whether access was ALLOWED or BLOCKED (FR-003, FR-006).

**Independent Test**: Trigger a build that accesses allowed and blocked domains; open accessed-domains stream and GET details; verify each entry has `domain` and `verdict` (ALLOWED or BLOCKED).

- [x] T018 [US2] Add @Operation and @ApiResponse for `subscribeToAccessedDomains` and document SSE event `accessed-domains` payload (domain, verdict) in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ImageBuildController.java`

**Checkpoint**: User Story 2 contract is documented; payload shape already satisfies FR-003 from Phase 2–3.

---

## Phase 5: User Story 3 — No domain stream when Cilium disabled (Priority: P3)

**Goal**: When Cilium is not enabled, the system does not expose or populate the domain access stream (FR-004); administrator sees stream unavailable or no data.

**Independent Test**: With Cilium disabled, call GET .../accessed-domains and verify 404 or empty/unavailable response; run a build and verify no domain entries are produced.

- [x] T019 [US3] When Cilium is disabled, return 404 or otherwise indicate domain stream unavailable for `GET /api/v1/images/builds/{id}/accessed-domains` (use existing Cilium flag e.g. CiliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled() or app property) in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ImageBuildController.java` or `src/main/java/com/epam/aidial/deployment/manager/service/ImageBuildLogsService.java`

**Checkpoint**: User Story 3 complete; domain stream is gated by Cilium enabled.

---

## Phase 6: Hubble integration (feeds stream)

**Purpose**: Hubble relay gRPC client runs during build and appends accessed domains to image definition so the stream and details have data (research.md).

- [x] T020 Create HubbleRelayService in `src/main/java/com/epam/aidial/deployment/manager/service/hubble/HubbleRelayService.java`: connect to Hubble Relay (configurable host/port, e.g. app.hubble-relay.host, app.hubble-relay.port), call Observer.GetFlows with follow=true and FlowFilter.source_pod for build pod prefix (e.g. namespace/dm-base-{imageDefinitionId}), map Flow (Verdict, Dns.query) to domain + AccessVerdict, aggregate distinct domain (allowed if any FORWARDED else BLOCKED), call ImageDefinitionService.addAccessedDomain(s); handle errors and close stream on error (FR-008)
- [x] T021 Integrate Hubble stream start/stop with image build lifecycle: start Hubble stream when build starts (e.g. in ImageBuildRunner or first pipeline step that creates the job), stop when build reaches final status or fails; pass imageDefinitionId and pod namespace/name prefix; call resetAccessedDomains at build start in `src/main/java/com/epam/aidial/deployment/manager/service/ImageBuildRunner.java` and pipeline steps (e.g. BaseImageBuildStep, ImageCopyStep) or a dedicated pipeline hook that starts/stops HubbleRelayService for the build

**Checkpoint**: Builds with Cilium enabled populate accessed domains via Hubble; stream and details show real data.

---

## Phase 7: Polish and cross-cutting

**Purpose**: Quality, tests, and validation.

- [x] T022 [P] Run `./gradlew checkstyleMain checkstyleTest` and fix any issues; run `./gradlew testFast` and fix failing tests
- [x] T023 Extend ImageBuildLogsServiceTest for `streamAccessedDomains` and ImageBuildControllerTest for `subscribeToAccessedDomains` per quickstart in `src/test/java/com/epam/aidial/deployment/manager/service/ImageBuildLogsServiceTest.java` and `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/ImageBuildControllerTest.java`
- [ ] T024 Run quickstart validation: build, run, trigger build, open GET .../accessed-domains and GET .../details and verify behavior per `specs/004-cilium-domain-stream/quickstart.md`

---

## Dependencies and execution order

### Phase dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 if Hubble codegen is required for T020; otherwise T003–T014 can proceed after T001–T002. Phase 2 BLOCKS Phases 3–5.
- **Phase 3 (US1)**: Depends on Phase 2 completion.
- **Phase 4 (US2)**: Depends on Phase 3 (endpoint exists to document).
- **Phase 5 (US3)**: Depends on Phase 3 (endpoint exists to guard).
- **Phase 6 (Hubble)**: Depends on Phase 2 (ImageDefinitionService.addAccessedDomain(s), proto codegen); can run in parallel with Phase 4–5 after Phase 3.
- **Phase 7 (Polish)**: Depends on all implementation phases.

### User story dependencies

- **US1 (P1)**: After Phase 2 — no dependency on US2/US3.
- **US2 (P2)**: After US1 — documentation and contract; data shape already in place from Phase 2–3.
- **US3 (P3)**: After US1 — guard on same endpoint.

### Parallel opportunities

- **Phase 1**: T002 [P] (proto files) can run in parallel with T001 once plugin is decided.
- **Phase 2**: T003, T004, T005 (migrations) can run in parallel; T006, T007, T013 (model/DTO) can run in parallel; T008, T009 after T006–T007.
- **Phase 6**: T020 (HubbleRelayService) and T021 (pipeline integration) are sequential (T021 uses T020).
- **Phase 7**: T022 [P] (checkstyle/tests) can run in parallel with T023.

---

## Parallel example: Phase 2

```text
# Migrations in parallel:
T003: H2 migration
T004: POSTGRES migration
T005: MS_SQL_SERVER migration

# Model and DTO in parallel (after migrations or alongside):
T006: AccessVerdict enum
T007: AccessedDomain model
T013: AccessedDomainDto + ImageBuildDetailsDto.accessedDomains
```

---

## Implementation strategy

### MVP first (User Story 1 only)

1. Complete Phase 1 (proto setup).
2. Complete Phase 2 (foundation).
3. Complete Phase 3 (US1 — stream endpoint, replay, status, zero-domains).
4. **Stop and validate**: Call GET .../accessed-domains during/after a build; verify stream and details (may be empty until Hubble is integrated).
5. Deploy/demo stream and details endpoint.

### Incremental delivery

1. Phase 1 + 2 → storage and details support accessed domains.
2. Phase 3 (US1) → stream endpoint (MVP).
3. Phase 4 (US2) → document verdict in contract.
4. Phase 5 (US3) → Cilium-disabled guard.
5. Phase 6 → Hubble populates data.
6. Phase 7 → tests and polish.

### Suggested MVP scope

**Phase 1 + Phase 2 + Phase 3** (Setup + Foundational + US1): Delivers dedicated accessed-domains stream, replay, status, zero-domains message, and details DTO. Stream may be empty until Phase 6 (Hubble) is implemented.
