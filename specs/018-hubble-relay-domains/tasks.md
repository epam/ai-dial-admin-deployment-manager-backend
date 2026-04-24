# Tasks: Hubble Relay Domain Streaming

**Input**: Design documents from `specs/018-hubble-relay-domains/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.
**Tests**: No test implementation tasks (not explicitly requested in spec). Each phase includes a verification checkpoint.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths included in all descriptions

## Path Prefix

All Java source paths are relative to:
`src/main/java/com/epam/aidial/deployment/manager/`

---

## Phase 1: Setup — Gradle + Proto Codegen

**Purpose**: Wire in gRPC/Protobuf build tooling and Cilium proto files so generated stubs are available to the rest of the implementation.

- [X] T001 Add gRPC and Protobuf dependencies to `build.gradle`: `implementation("io.grpc:grpc-stub:1.75.0")`, `implementation("io.grpc:grpc-protobuf:1.75.0")`, `implementation("com.google.protobuf:protobuf-java:3.25.3")`, and apply plugin `id 'com.google.protobuf' version '0.9.4'`
- [X] T002 [P] Copy Cilium proto files into `src/main/proto/`: `observer/observer.proto` (Observer service with GetFlows RPC), `flow/flow.proto` (Flow, DNS, Layer4, Verdict messages), `flow/types.proto` (FlowType enum) — source: `cilium/hubble` repo `vendor/github.com/cilium/cilium/api/v1/observer/` and `api/v1/flow/`
- [X] T003 Configure the `protobuf` Gradle plugin block in `build.gradle` (protoc version 3.25.3, grpc plugin 1.75.0, `generateProtoTasks { all().each { task -> task.plugins { grpc {} } } }`, wire generated sources into `sourceSets.main.java.srcDirs`) and verify `./gradlew compileJava` succeeds with generated stubs

**Checkpoint**: `./gradlew compileJava` passes; `ObserverGrpc`, `FlowOuterClass`, and `ObserverOuterClass` classes exist in generated sources.

---

## Phase 2: Foundational — Config, Data Layer, Hubble Core Components

**Purpose**: All persistence infrastructure, configuration, and Hubble core components that every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] Create `CiliumVerdict` enum in `model/CiliumVerdict.java` with values `ALLOWED` and `BLOCKED` (no Spring annotations; plain enum)
- [X] T005 [P] Create `DomainEntryDto` Java record in `web/dto/DomainEntryDto.java` with fields `String domain` and `CiliumVerdict verdict`
- [X] T006 [P] Create `HubbleRelayProperties` `@ConfigurationProperties(prefix = "hubble-relay")` class in `configuration/HubbleRelayProperties.java` with fields: `boolean enabled`, `String host`, `String namespace`, `String podLabelSelector`, `int port`, `int connectRetryCount`, `long connectRetryIntervalMs`, `boolean tlsEnabled`, `String caCertPath` — declare fields without Java initializers (defaults go in `application.yml` only, per constitution); note: `host` is used by the direct-connect (NodePort/LB) path in `HubbleRelayGrpcChannelFactory`; port-forward mode derives the effective address as `localhost:localPort` at runtime. Hubble Relay integration only produces DNS flows when `CILIUM_NETWORK_POLICIES_ENABLED=true` — `HubbleDomainFlowService` (T019) emits a WARN if Hubble is enabled without Cilium policies
- [X] T007 [P] Add `hubble-relay` configuration block to `src/main/resources/application.yml` with env-var-backed defaults: `enabled: ${HUBBLE_RELAY_ENABLED:false}`, `host: ${HUBBLE_RELAY_HOST:hubble-relay.cilium.svc.cluster.local}`, `namespace: ${HUBBLE_RELAY_NAMESPACE:cilium}`, `pod-label-selector: ${HUBBLE_RELAY_POD_LABEL_SELECTOR:k8s-app=hubble-relay}`, `port: ${HUBBLE_RELAY_PORT:80}`, `connect-retry-count: ${HUBBLE_RELAY_CONNECT_RETRY_COUNT:3}`, `connect-retry-interval-ms: ${HUBBLE_RELAY_CONNECT_RETRY_INTERVAL_MS:2000}`, `tls-enabled: ${HUBBLE_RELAY_TLS_ENABLED:false}`, `ca-cert-path: ${HUBBLE_RELAY_CA_CERT_PATH:}`
- [X] T008 [P] Create Flyway migration `V1.59__CreateImageBuildDomainEntriesTable.sql` in all three vendor directories (`src/main/resources/db/migration/H2/`, `POSTGRES/`, `MS_SQL_SERVER/`) as specified in `data-model.md`: `image_build_domain_entries` table with `id` (BIGINT auto-increment PK), `image_definition_id` (UUID/UNIQUEIDENTIFIER FK → `image_definition(id)` ON DELETE CASCADE), `domain` (VARCHAR 255), `verdict` (VARCHAR 10), `observed_at` (BIGINT), and UNIQUE constraint `(image_definition_id, domain, verdict)`
- [X] T009 [P] Create Flyway migration `V1.60__CreateDeploymentDomainEntriesTable.sql` in all three vendor directories as specified in `data-model.md`: `deployment_domain_entries` table with `id` (BIGINT auto-increment PK), `deployment_id` (VARCHAR 36 FK → `deployment(id)` ON DELETE CASCADE), `domain` (VARCHAR 255), `verdict` (VARCHAR 10), `observed_at` (BIGINT), and UNIQUE constraint `(deployment_id, domain, verdict)`
- [X] T010 [P] Create `ImageBuildDomainEntryEntity` JPA entity in `dao/entity/ImageBuildDomainEntryEntity.java`: `@Entity`, `@Table(name = "image_build_domain_entries")`, `@Getter @Setter @NoArgsConstructor @EqualsAndHashCode(of = "id") @ToString` (do NOT use `@Data` — it generates `equals`/`hashCode` over all fields which causes JPA collection membership bugs; use `@EqualsAndHashCode(of = "id")` so identity is PK-based), fields: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`, `UUID imageDefinitionId`, `String domain`, `@Enumerated(EnumType.STRING) CiliumVerdict verdict`, `long observedAt`
- [X] T011 [P] Create `DeploymentDomainEntryEntity` JPA entity in `dao/entity/DeploymentDomainEntryEntity.java`: `@Entity`, `@Table(name = "deployment_domain_entries")`, `@Getter @Setter @NoArgsConstructor @EqualsAndHashCode(of = "id") @ToString` (do NOT use `@Data` — same reason as T010: PK-based equality required for JPA), fields: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`, `String deploymentId`, `String domain`, `@Enumerated(EnumType.STRING) CiliumVerdict verdict`, `long observedAt`
- [X] T012 [P] Create `ImageBuildDomainEntryJpaRepository` Spring Data JPA interface in `dao/jpa/ImageBuildDomainEntryJpaRepository.java` extending `JpaRepository<ImageBuildDomainEntryEntity, Long>` with method `List<ImageBuildDomainEntryEntity> findByImageDefinitionIdOrderById(UUID imageDefinitionId)`
- [X] T013 [P] Create `DeploymentDomainEntryJpaRepository` Spring Data JPA interface in `dao/jpa/DeploymentDomainEntryJpaRepository.java` extending `JpaRepository<DeploymentDomainEntryEntity, Long>` with methods `List<DeploymentDomainEntryEntity> findByDeploymentIdOrderById(String deploymentId)` and `void deleteByDeploymentId(String deploymentId)`
- [X] T014 [P] Create `ImageBuildDomainEntryRepository` domain repository wrapper in `dao/repository/ImageBuildDomainEntryRepository.java`: `@Repository @LogExecution @Transactional`, methods `saveIgnoreDuplicate(UUID imageDefinitionId, String domain, CiliumVerdict verdict, long observedAt)` (catches `DataIntegrityViolationException` and discards it — add inline comment `// expected: concurrent insert of duplicate (domain, verdict) pair; dedup enforced by UNIQUE constraint` to satisfy constitution anti-pattern #2 "explicitly document why suppression is safe") and `findAllByImageDefinitionId(UUID imageDefinitionId)` returning `List<ImageBuildDomainEntryEntity>` ordered by id
- [X] T015 [P] Create `DeploymentDomainEntryRepository` domain repository wrapper in `dao/repository/DeploymentDomainEntryRepository.java`: `@Repository @LogExecution @Transactional`, methods `saveIgnoreDuplicate(String deploymentId, String domain, CiliumVerdict verdict, long observedAt)` (catches `DataIntegrityViolationException` and discards it — add inline comment `// expected: concurrent insert of duplicate (domain, verdict) pair; dedup enforced by UNIQUE constraint` per constitution anti-pattern #2), `findAllByDeploymentId(String deploymentId)`, and `deleteAllByDeploymentId(String deploymentId)`
- [X] T016 [P] Implement `HubbleDomainFilter` in `kubernetes/hubble/HubbleDomainFilter.java`: `@Component @LogExecution`, method `boolean isExternalDomain(String queryName)` that strips trailing `.` from the query name, then returns `false` if the result: (1) ends with `.cluster.local` OR (2) contains `.svc.cluster.local` OR (3) contains `.internal.` — otherwise returns `true`. Do NOT use the looser `contains(".cluster.")` check — it would incorrectly filter valid external domains like `my-cluster.example.com`. The three conditions above match exactly the suffixes specified in FR-009
- [X] T017 Implement `HubbleRelayGrpcChannelFactory` in `kubernetes/hubble/HubbleRelayGrpcChannelFactory.java`: `@Component @LogExecution @RequiredArgsConstructor`, inject `HubbleRelayProperties` and `deployKubeClient` (Fabric8 KubernetesClient bean), method `ManagedChannel createChannel()` that: (1) finds the `hubble-relay` pod by label selector in the configured namespace, (2) opens `client.pods().inNamespace(namespace).withName(podName).portForward(port)` via `LocalPortForward`, (3) if `properties.isTlsEnabled()`: build channel with `ManagedChannelBuilder.forAddress("localhost", localPortForward.getLocalPort()).useTransportSecurity().sslContext(GrpcSslContexts.forClient().trustManager(new File(properties.getCaCertPath())).build()).build()`; otherwise: `ManagedChannelBuilder.forAddress("localhost", localPortForward.getLocalPort()).usePlaintext().build()`; the channel and port-forward must be closeable together. **Design note — per-observation lifecycle**: the channel is created per `observe()` call (not a singleton at startup). Each `LocalPortForward` opens exactly one WebSocket tunnel through the K8s API; a shared/singleton channel would require a long-lived tunnel with external reconnect logic. Per-observation creation keeps each build/deployment observation self-contained and automatically closes the tunnel on completion. `properties.getHost()` is available for a future direct-connect (NodePort/LB) path where the channel would target `properties.getHost():properties.getPort()` directly instead of `localhost:localPortForward.getLocalPort()`
- [X] T018 Implement `HubbleFlowObserver` in `kubernetes/hubble/HubbleFlowObserver.java`: `@Component @LogExecution @RequiredArgsConstructor`, inject `HubbleRelayGrpcChannelFactory` and `HubbleDomainFilter`; method `void observe(String podNamespace, String podLabelSelector, Consumer<DomainEntry> onEntry)` (filters by **namespace + label selector**, not pod name — a pod may not exist yet when observation starts and labels are stable across restarts) that: opens a gRPC channel via factory, creates `ObserverGrpc.ObserverBlockingStub`, calls `GetFlows` with a `GetFlowsRequest` using `FlowFilter` scoped to `podNamespace` and the given `podLabelSelector` with `EventType.L7` (DNS proxy flows); for each received `Flow` that is `FlowType.L7` with a DNS response: strip trailing `.` from query name, call `HubbleDomainFilter.isExternalDomain()`, map verdict (`FORWARDED` → `CiliumVerdict.ALLOWED`, `DROPPED` → `CiliumVerdict.BLOCKED`), invoke `onEntry` callback; runs blocking on the calling thread; tears down channel on exit or interrupt. `DomainEntry` is a top-level record in `model/DomainEntry.java` with fields `{String domain, CiliumVerdict verdict, long observedAt}` — placed in `model/` so both `kubernetes/` and `service/` layers can reference it without cross-layer imports
- [X] T019 Implement `HubbleDomainFlowService` in `service/HubbleDomainFlowService.java`: `@Service @LogExecution @RequiredArgsConstructor`; maintains `ConcurrentHashMap<String, Future<?>> activeBuildObservations` and `ConcurrentHashMap<String, Future<?>> activeDeploymentObservations`; inject `HubbleFlowObserver`, `ImageBuildDomainEntryRepository`, `DeploymentDomainEntryRepository`, `HubbleRelayProperties`, and `@Value("${app.cilium-network-policies-enabled}") boolean ciliumNetworkPoliciesEnabled` (no inline default — `application.yml` already declares `cilium-network-policies-enabled: ${CILIUM_NETWORK_POLICIES_ENABLED:false}`, consistent with how `CiliumNetworkPolicyCreator` reads the same property); use a **dedicated virtual-thread executor** (`private final ExecutorService hubbleExecutor = Executors.newVirtualThreadPerTaskExecutor()` as a field, NOT the `sse-streamer` executor — Hubble threads block on gRPC for the entire build/deployment duration and must not starve SSE HTTP threads); method `void startBuildObservation(UUID imageDefinitionId, String podNamespace, String podLabelSelector)`: if `!properties.isEnabled()` return immediately; if `!ciliumNetworkPoliciesEnabled` log WARN "Hubble Relay is enabled but CILIUM_NETWORK_POLICIES_ENABLED=false — Cilium is not enforcing DNS policies so no DNS flows will be visible; domain streaming will produce no events" and return; submit task to `hubbleExecutor` that retries `HubbleFlowObserver.observe(podNamespace, podLabelSelector, callback)` up to `connectRetryCount` times with `connectRetryIntervalMs` delay; on each `DomainEntry` callback calls `imageBuildDomainEntryRepository.saveIgnoreDuplicate(...)`; on retry exhaustion log warning and return; method `void stopBuildObservation(UUID imageDefinitionId)`: cancels and removes the future; symmetric `startDeploymentObservation(String deploymentId, String podNamespace, String podLabelSelector)` / `stopDeploymentObservation(String deploymentId)` for deployment scope — apply same `ciliumNetworkPoliciesEnabled` guard; also expose a read-only query method `List<ImageBuildDomainEntryEntity> getDomainEntriesForBuild(UUID imageDefinitionId)` that delegates to `imageBuildDomainEntryRepository.findAllByImageDefinitionId(imageDefinitionId)` — this method is called by `ImageBuildController` (T024) so the controller never touches the DAO layer directly

**Checkpoint**: Run `./gradlew testFast` — Flyway migrations apply, entities are created, `HubbleDomainFilter` can be unit-tested without Hubble Relay connectivity.

---

## Phase 3: User Story 1 — Real-Time Domain Streaming During Image Build (Priority: P1) 🎯 MVP

**Goal**: Domain events appear interleaved with log and status events in the build log SSE stream while a build is active.

**Independent Test**: Trigger a build with `HUBBLE_RELAY_ENABLED=true`, open `GET /api/v1/images/builds/{id}/logs`, observe `event: domain` events appearing alongside `event: logs` and `event: status` events. With `HUBBLE_RELAY_ENABLED=false`, verify no domain events and no regressions.

- [X] T020 [US1] Integrate `HubbleDomainFlowService` into `ImageBuildRunner` in `service/ImageBuildRunner.java`: inject `HubbleDomainFlowService`; in `startDockerImagePipeline()` (and `buildMcpImage()` where a pod is started) call `hubbleDomainFlowService.startBuildObservation(imageDefinitionId, podNamespace, podLabelSelector)` before submitting the pipeline task, and call `hubbleDomainFlowService.stopBuildObservation(imageDefinitionId)` in a `finally` block after the pipeline completes. **Exact label selector**: Kubernetes automatically stamps all pods created by a Job with the label `job-name=<jobName>`. The job name is `K8sNamingUtils.generateName(imageDefinitionId)` — the same value used by `JobRunner` when creating the Job. So: `podLabelSelector = "job-name=" + K8sNamingUtils.generateName(imageDefinitionId)` (reuse `K8sNamingUtils` and the constant `JobRunner.JOB_NAME_LABEL = "job-name"` — do not hardcode the string). **Exact namespace**: inject `@Value("${app.kubernetes.build-namespace}")` or obtain from the same configuration source used by `JobRunner.jobSpecification.getNamespace()`
- [X] T021 [US1] Extend `ImageBuildLogsService` in `service/ImageBuildLogsService.java`: inject `ImageBuildDomainEntryRepository` and `Jackson ObjectMapper`; in `startLogStreaming()`, after emitting new log lines each poll iteration, fetch domain entries from `imageBuildDomainEntryRepository.findAllByImageDefinitionId(imageDefinitionId)` from `domainIndex` offset onward, emit each as `emitter.send(SseEmitter.event().name("domain").data(objectMapper.writeValueAsString(new DomainEntryDto(entry.getDomain(), entry.getVerdict()))))`, advance `domainIndex`; when `HUBBLE_RELAY_ENABLED=false` (inject `HubbleRelayProperties`), skip domain polling entirely

**Checkpoint**: `./gradlew testFast` passes. With Hubble Relay enabled, build log stream emits `event: domain` frames. With it disabled, stream is unchanged.

---

## Phase 4: User Story 2 — Full Domain Access List in Build Details (Priority: P2)

**Goal**: `GET /api/v1/images/builds/{id}/details` returns a `domains` list containing all captured (domain, verdict) pairs for that build run.

**Independent Test**: Complete a build with Hubble Relay enabled, call `/details`, verify `domains` field is present and contains entries with correct verdicts.

- [X] T022 [US2] Update `ImageBuildDetailsDto` record in `web/dto/ImageBuildDetailsDto.java` to add a nullable `@Nullable List<@NotNull DomainEntryDto> domains` field
- [X] T023 [US2] Update `ImageBuildDetailsDtoMapper` in `web/mapper/ImageBuildDetailsDtoMapper.java`: add a mapping method `List<DomainEntryDto> toDomainDtos(List<ImageBuildDomainEntryEntity> entities)` converting `ImageBuildDomainEntryEntity` to `DomainEntryDto` (domain and verdict fields only — no HubbleRelayProperties injection in the mapper); update `toDto()` to accept a `List<ImageBuildDomainEntryEntity> domainEntries` parameter — the mapper always maps whatever list is passed; the controller (T024) is responsible for passing `null` when Hubble Relay is disabled
- [X] T024 [US2] Update `ImageBuildController` in `web/controller/ImageBuildController.java`: inject `HubbleDomainFlowService` and `HubbleRelayProperties` (do NOT inject any DAO/repository — web layer MUST NOT call dao directly per constitution Principle I); in `getImageBuildLogsById()`, when Hubble Relay is enabled call `hubbleDomainFlowService.getDomainEntriesForBuild(id)` (service layer) to fetch domain entries, pass them to the updated mapper; pass `null` when Hubble is disabled. The mapper (T023) accepts the list regardless and returns the `ImageBuildDetailsDto`

**Checkpoint**: `GET /api/v1/images/builds/{id}/details` response includes `"domains":[...]` (may be `null` when Hubble disabled). `./gradlew testFast` passes.

---

## Phase 5: User Story 3 — Replay Domain Events for Completed Build (Priority: P3)

**Goal**: A client connecting to a completed build stream receives all captured domain events replayed alongside log lines before the terminal status event.

**Independent Test**: Complete a build with Hubble Relay enabled, then open `GET /api/v1/images/builds/{id}/logs` for the completed build — all domain events must appear in the stream before it closes with a terminal `status` event.

- [X] T025 [US3] Verify and adjust `ImageBuildLogsService` in `service/ImageBuildLogsService.java` to handle replay correctly for completed builds: the polling loop emits log lines first (logIndex 0→end), then domain entries (domainIndex 0→end), then the final status event, then calls `emitter.complete()` — this order (logs before domains) is the approved replay ordering per updated FR-007 (individual log lines have no timestamps; domain entries follow all logs in insertion order). Confirm `domainIndex` starts at 0 for each new stream connection.

**Checkpoint**: A completed-build stream returns all domain entries in the SSE response before closing. `./gradlew testFast` passes.

---

## Phase 6: User Story 4 — Real-Time Domain Streaming for Running Deployment (Priority: P4)

**Goal**: Domain events appear interleaved with log events in the deployment pod log SSE stream. Entries are cleared on each new deploy activation and replayed on reconnect.

**Independent Test**: Activate a deployment with Hubble Relay enabled, open `GET /api/v1/deployments/{id}/pods/{podId}/logs`, observe `event: domain` events interleaved with `event: logs` events. Re-trigger deploy and verify domain entries are cleared. Reconnect to an active pod stream and verify previously captured entries are replayed.

- [X] T026 [US4] Integrate `HubbleDomainFlowService` and `DeploymentDomainEntryRepository` into `AbstractDeploymentManager` in `service/deployment/AbstractDeploymentManager.java`: inject both beans; at the start of `deploy(String id)`: call `deploymentDomainEntryRepository.deleteAllByDeploymentId(id)` to clear previous entries (FR-014), then call `hubbleDomainFlowService.startDeploymentObservation(id, namespace, podLabelSelector)` where `namespace` is the existing `this.namespace` field and `podLabelSelector` is `getServiceNameLabel() + "=" + getServiceName(id)`. **Note on label key**: `getServiceNameLabel()` is the abstract method already used by CNP creation in `createCiliumNetworkPolicy()` — it returns the correct pod-selector label key per deployment type (Knative: `serving.knative.dev/service`, NIM: `app.kubernetes.io/name`, KServe: `serving.kserve.io/inferenceservice`). Reuse this method; do not hardcode any label key string
- [X] T027 [US4] Extend `DeploymentLogsService` in `service/deployment/DeploymentLogsService.java` to add parallel domain polling: inject `DeploymentDomainEntryRepository`, `HubbleRelayProperties`, `ObjectMapper`, and `@Value("${app.sse.poll-interval-ms:1000}") long pollIntervalMs` (this is a NEW injection — `DeploymentLogsService` does not currently use a poll interval; add alongside the existing fields); in `startPodStreaming()`, if Hubble Relay is enabled, submit a second `Future` to `executorService` that polls `deploymentDomainEntryRepository.findAllByDeploymentId(id)` from a `domainIndex` offset on each `pollIntervalMs` sleep cycle and emits `event: domain` SSE events; use `synchronized(emitter)` to guard concurrent writes from the log-reader future and the domain-poller future. **Dual-future cancellation**: `startPodStreaming()` currently returns `SafeAutoCloseable` wrapping one future as `() -> future.cancel(true)`. With two futures, capture both before returning and return a composite: `var logFuture = executorService.submit(...); var domainFuture = executorService.submit(...); return () -> { logFuture.cancel(true); domainFuture.cancel(true); };` — when Hubble is disabled, `domainFuture` is null so guard: `return () -> { logFuture.cancel(true); if (domainFuture != null) domainFuture.cancel(true); };`

**Checkpoint**: Deployment pod log stream emits `event: domain` events. Re-deploying clears the entries. Reconnecting to an active pod stream replays all previously captured entries. `./gradlew testFast` passes.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, schema regen, and final verification.

- [ ] T029 [P] Update `docs/configuration.md` with Hubble Relay configuration section: document all `HUBBLE_RELAY_*` env vars (`HUBBLE_RELAY_ENABLED`, `HUBBLE_RELAY_HOST`, `HUBBLE_RELAY_NAMESPACE`, `HUBBLE_RELAY_POD_LABEL_SELECTOR`, `HUBBLE_RELAY_PORT`, `HUBBLE_RELAY_CONNECT_RETRY_COUNT`, `HUBBLE_RELAY_CONNECT_RETRY_INTERVAL_MS`, `HUBBLE_RELAY_TLS_ENABLED`, `HUBBLE_RELAY_CA_CERT_PATH`) with types, defaults, and descriptions matching `quickstart.md`; include note that `HUBBLE_RELAY_ENABLED=true` requires `CILIUM_NETWORK_POLICIES_ENABLED=true`
- [ ] T030 Run `./gradlew generateDbSchema` to regenerate `docs/db-schema.md` after the V1.59 and V1.60 migrations are in place; commit the updated file
- [X] T031 Run `./gradlew checkstyleMain checkstyleTest` and resolve all Checkstyle violations in new and modified files (Google Java Style, 180-char line limit, no wildcard imports, `@LogExecution` on all Spring components)
- [X] T032 Run `./gradlew testFast` (full H2 suite) and ensure no regressions in existing tests; run `./gradlew test` for the complete vendor matrix before PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 completion (needs generated gRPC stubs for T018–T019) — **BLOCKS all user stories**
- **Phase 3 (US1, P1)**: Depends on Phase 2 — MVP
- **Phase 4 (US2, P2)**: Depends on Phase 2 (needs domain entity and repository) — can start in parallel with Phase 3
- **Phase 5 (US3, P3)**: Depends on Phase 3 (replay is part of the same polling implementation) — verify after US1
- **Phase 6 (US4, P4)**: Depends on Phase 2 — can start in parallel with Phase 3 and Phase 4
- **Phase 7 (Polish)**: Depends on all desired user stories complete

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2. No dependency on US2–US4.
- **US2 (P2)**: Starts after Phase 2. No dependency on US1 at implementation level (can be implemented before US1 even records data, will just return empty list).
- **US3 (P3)**: Logically depends on US1 (needs domain entries recorded to replay); implementation is a correctness check on the US1 streaming loop.
- **US4 (P4)**: Starts after Phase 2. No dependency on US1–US3.

### Within Each Phase

- All `[P]`-marked tasks within a phase can run in parallel
- T017 → T018 → T019 are sequential (factory → observer → service)
- T020 → T021 are sequential (ImageBuildRunner before ImageBuildLogsService so observation is active before stream opens)

---

## Parallel Opportunities

```
# Phase 2 — All T004–T016 can run in parallel (different files):
T004: CiliumVerdict enum
T005: DomainEntryDto record
T006: HubbleRelayProperties
T007: application.yml defaults
T008: V1.59 migrations (all vendors)
T009: V1.60 migrations (all vendors)
T010: ImageBuildDomainEntryEntity
T011: DeploymentDomainEntryEntity
T012: ImageBuildDomainEntryJpaRepository
T013: DeploymentDomainEntryJpaRepository
T014: ImageBuildDomainEntryRepository
T015: DeploymentDomainEntryRepository
T016: HubbleDomainFilter

# Phase 4 and Phase 6 can run in parallel once Phase 2 is done:
(US2 tasks in parallel with US4 tasks)

# Phase 7 — T029 and T030 can run in parallel:
T029: docs/configuration.md
T030: ./gradlew generateDbSchema
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (Gradle + proto)
2. Complete Phase 2: Foundational (all T004–T019)
3. Complete Phase 3: User Story 1 (T020–T021)
4. **STOP and VALIDATE**: Build log stream emits `event: domain` events; Hubble disabled mode leaves builds unchanged
5. Continue with Phase 4 (US2) and Phase 6 (US4) in parallel

### Incremental Delivery

1. Phase 1 + Phase 2 → Core infrastructure ready
2. Phase 3 (US1) → Real-time build domain streaming (MVP)
3. Phase 4 (US2) + Phase 5 (US3) → Build details API + completed build replay
4. Phase 6 (US4) → Deployment domain streaming
5. Phase 7 → Docs + schema + clean build

---

## Notes

- `[P]` tasks = different files, no shared state dependencies
- `[Story]` label maps tasks to specific user stories for traceability
- Constitution compliance: `@LogExecution` on ALL new `@Service`, `@Component`, `@Repository` classes; no `@Transactional` on controllers; all Kubernetes/gRPC calls in `kubernetes/hubble/` package only; defaults in `application.yml` only
- Deduplication strategy: catch `DataIntegrityViolationException` in repository `saveIgnoreDuplicate()` methods rather than pre-checking — avoids race conditions (Decision 3)
- Port-forward uses plaintext localhost gRPC channel (`usePlaintext()`) — security via K8s API TLS + RBAC; set `HUBBLE_RELAY_TLS_ENABLED=true` only when upgrading to NodePort/LoadBalancer (FR-013)
- `DomainEntry` is a top-level record in `model/DomainEntry.java` — accessible from both `kubernetes/hubble/` and `service/` layers without cross-layer imports
- Hubble observation threads use a **dedicated virtual-thread executor** (`Executors.newVirtualThreadPerTaskExecutor()`) in `HubbleDomainFlowService` — NOT the `sse-streamer` executor, to avoid starving SSE HTTP threads
- Run `./gradlew checkstyleMain checkstyleTest` after each phase to catch style issues early
