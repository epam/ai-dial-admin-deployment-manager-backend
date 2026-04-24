# Implementation Plan: Hubble Relay Domain Streaming

**Branch**: `018-hubble-relay-domains` | **Date**: 2026-04-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/018-hubble-relay-domains/spec.md`

## Summary

When Hubble Relay is enabled, the system captures real-time DNS flow verdicts (ALLOWED / BLOCKED) from image build pods and deployment pods via a cross-cluster gRPC connection to the Hubble Relay `Observer` service. Captured domain entries are persisted per build run and per deployment activation, deduplicated by (domain, verdict), and streamed as `event: domain` SSE events interleaved with existing `logs` and `status` events in the build log stream and the deployment pod log stream. The cross-cluster gRPC channel is tunnelled through the existing Kubernetes API port-forward mechanism, requiring no new infrastructure.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2 (existing; provides `LocalPortForward`); gRPC 1.75.0 (`grpc-netty-shaded` already constrained, add `grpc-stub`, `grpc-protobuf`); Protobuf 3.25.3 (`protobuf-java`); com.google.protobuf Gradle plugin 0.9.4; MapStruct 1.6.0; Lombok 8.10; `Executors.newVirtualThreadPerTaskExecutor()` for Hubble observation threads (Java 21 built-in, no new dependency)
**Storage**: H2 2.3.232 (dev/test), PostgreSQL 42.7.8, SQL Server 13.2.1 — Flyway migrations V1.58 and V1.59 across all three vendors
**Testing**: JUnit 5, Testcontainers 1.21.3, AssertJ; `./gradlew testFast` for H2-only dev loop; `./gradlew test` for full vendor matrix
**Target Platform**: Linux server / Kubernetes pod (Spring Boot executable JAR)
**Project Type**: web-service (REST + SSE)
**Performance Goals**: First `domain` SSE event within the same 1000 ms poll window as the first `logs` event; Hubble observer connection startup within the retry budget (configurable fixed retries × short interval)
**Constraints**: Hubble Relay unavailability MUST NOT fail builds or deployments; no new Kubernetes service types or firewall rules required; no mutual TLS — server-side certificate validation only for future NodePort/LB upgrade; port-forward approach uses plaintext localhost channel (K8s API TLS + RBAC provides transport security)
**Scale/Scope**: Per-build (imageDefinitionId UUID) and per-deployment (deployment_id VARCHAR) domain entry tables; (domain, verdict) dedup limits row count; no artificial cap

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Strict Layered Architecture | ✅ PASS | gRPC calls isolated to `kubernetes/hubble/`; service layer calls kubernetes layer |
| Transactional Discipline | ✅ PASS | `@Transactional` only on service and DAO layers |
| Kubernetes Isolation | ✅ PASS | `HubbleRelayGrpcChannelFactory`, `HubbleFlowObserver` in `kubernetes/hubble/` |
| Observability First | ✅ PASS | `@LogExecution` on all new Spring components |
| Security by Configuration | ✅ PASS | `HUBBLE_RELAY_ENABLED` flag; TLS trust store configurable |
| Configuration defaults in `application.yml` | ✅ PASS | All `HubbleRelayProperties` defaults in `application.yml` only |
| Multi-vendor migrations | ✅ PASS | V1.58 and V1.59 created for H2, POSTGRES, MS_SQL_SERVER |
| `docs/configuration.md` update | ✅ REQUIRED | Task added to update docs with new env vars |
| `./gradlew generateDbSchema` | ✅ REQUIRED | Final task to regenerate `docs/db-schema.md` |
| No polling loops for K8s state | ✅ PASS | gRPC streaming is blocking read loop on virtual thread (not a K8s state polling loop) |
| `@LogExecution` on all Spring components | ✅ PASS | Required on `HubbleDomainFlowService`, `HubbleRelayGrpcChannelFactory`, `HubbleDomainFilter`, repositories |

*Post-design re-check: all gates still pass. Complexity justification below covers the gRPC addition.*

## Project Structure

### Documentation (this feature)

```text
specs/018-hubble-relay-domains/
├── plan.md              # This file
├── research.md          # Phase 0 — connectivity, stubs, dedup, lifecycle, filter, SSE integration, RBAC
├── data-model.md        # Phase 1 — entity definitions, migration SQL (all 3 vendors)
├── quickstart.md        # Phase 1 — prerequisites, env vars, verification
├── contracts/
│   ├── sse-domain-event.md         # Phase 1 — SSE event wire format
│   └── build-details-response.md   # Phase 1 — updated build details API response
└── tasks.md             # Phase 2 — /speckit.tasks output
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── dao/
│   ├── entity/
│   │   ├── ImageBuildDomainEntryEntity.java          (NEW) — image_build_domain_entries table
│   │   └── DeploymentDomainEntryEntity.java          (NEW) — deployment_domain_entries table
│   ├── jpa/
│   │   ├── ImageBuildDomainEntryJpaRepository.java   (NEW) — Spring Data JPA
│   │   └── DeploymentDomainEntryJpaRepository.java   (NEW) — Spring Data JPA
│   └── repository/
│       ├── ImageBuildDomainEntryRepository.java      (NEW) — domain repo wrapper
│       └── DeploymentDomainEntryRepository.java      (NEW) — domain repo wrapper
├── kubernetes/
│   └── hubble/
│       ├── HubbleRelayGrpcChannelFactory.java        (NEW) — creates LocalPortForward + ManagedChannel
│       └── HubbleFlowObserver.java                   (NEW) — gRPC GetFlows streaming, DNS filter, callback
├── model/
│   ├── CiliumVerdict.java                            (NEW) — ALLOWED, BLOCKED enum
│   └── DomainEntry.java                              (NEW) — record {String domain, CiliumVerdict verdict, long observedAt} used by kubernetes/hubble/ and service/ layers
├── service/
│   ├── HubbleDomainFlowService.java                  (NEW) — lifecycle (start/stop per scope), persistence
│   ├── ImageBuildLogsService.java                    (MODIFIED) — add domain polling alongside log polling
│   └── deployment/
│       └── DeploymentLogsService.java                (MODIFIED) — add parallel domain polling task
├── web/
│   ├── controller/
│   │   └── ImageBuildController.java                 (MODIFIED) — include domains in /details response
│   ├── dto/
│   │   ├── DomainEntryDto.java                       (NEW) — {domain, verdict} response record
│   │   └── ImageBuildDetailsDto.java                 (MODIFIED) — add domains list
│   └── mapper/
│       └── ImageBuildDetailsDtoMapper.java           (MODIFIED) — map domain entries
└── configuration/
    └── HubbleRelayProperties.java                    (NEW) — @ConfigurationProperties for Hubble config (enabled, host, namespace, podLabelSelector, port, retries, tlsEnabled, caCertPath)

src/main/proto/
├── observer/observer.proto                           (NEW) — Hubble Observer service
├── flow/flow.proto                                   (NEW) — Flow, DNS, Layer4 messages
└── flow/types.proto                                  (NEW) — FlowType enum

src/main/resources/
├── application.yml                                   (MODIFIED) — hubble relay config defaults
└── db/migration/
    ├── H2/
    │   ├── V1.58__CreateImageBuildDomainEntriesTable.sql (NEW)
    │   └── V1.59__CreateDeploymentDomainEntriesTable.sql (NEW)
    ├── POSTGRES/
    │   ├── V1.58__CreateImageBuildDomainEntriesTable.sql (NEW)
    │   └── V1.59__CreateDeploymentDomainEntriesTable.sql (NEW)
    └── MS_SQL_SERVER/
        ├── V1.58__CreateImageBuildDomainEntriesTable.sql (NEW)
        └── V1.59__CreateDeploymentDomainEntriesTable.sql (NEW)

build.gradle                                          (MODIFIED) — grpc-stub, grpc-protobuf, protobuf-java deps; protobuf plugin

docs/
├── configuration.md                                  (MODIFIED) — HUBBLE_RELAY_ENABLED and related properties
└── db-schema.md                                      (REGENERATED via ./gradlew generateDbSchema)
```

**Structure Decision**: Single-project Spring Boot service. New packages follow existing patterns: `kubernetes/hubble/` for all gRPC/K8s calls; `service/` for lifecycle coordination; `dao/entity/`, `dao/jpa/`, `dao/repository/` for persistence. No new modules added.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| New build dependency: gRPC + Protobuf codegen (`grpc-stub`, `grpc-protobuf`, `protobuf-java`, `com.google.protobuf` Gradle plugin) | Hubble Relay exposes only gRPC; no REST or WebSocket interface exists | Hand-writing stubs is maintenance-heavy; Cilium publishes no Java Maven artifact; gRPC Gradle plugin is the canonical approach |
| Proto files bundled in `src/main/proto/` | Same reason as above — no Maven artifact; proto files are stable and versioned with the spec | HTTP/2 service proxy eliminated (HTTP/1.1 only, no gRPC streaming) |
