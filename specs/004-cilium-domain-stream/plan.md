# Implementation Plan: Cilium Domain Access Streaming

**Branch**: `004-cilium-domain-stream` | **Date**: 2025-03-13 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/004-cilium-domain-stream/spec.md` and user implementation guidance.

## Summary

When Cilium is enabled, the system provides a dedicated SSE stream for domain access during image builds (separate from build logs). Each entry represents one distinct domain with outcome **allowed** (if any access was allowed by Cilium) or **blocked**. Implementation extends `ImageBuildController` with `subscribeToAccessedDomains`, extends `ImageBuildLogsService` with `streamAccessedDomains` (mirroring `subscribeToLogs` / `streamLogs`), adds a new Hubble relay integration service (gRPC at `hubble-relay.cilium.svc.cluster.local:80`) that runs during the build and appends accessed domains to the image definition (during ImageCopyStep, BaseImageBuildStep, etc.), and persists `accessed_domains` on `ImageDefinitionEntity` with Flyway migrations. Hubble logs are filtered by `imageDefinition.getId()`; DNS verdict is derived from log text (FORWARDED = allowed, DROPPED = blocked).

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.5.10, gRPC (Hubble Observer API), Flyway 11.14.0, MapStruct 1.6.0  
**Storage**: H2 / PostgreSQL / SQL Server — `image_definition.accessed_domains` (JSON) per vendor  
**Testing**: JUnit 5, AssertJ, existing ImageBuildLogsService / ImageBuildController patterns; Testcontainers for DB  
**Target Platform**: Kubernetes (backend service; Hubble relay in cluster)  
**Project Type**: web-service (REST + SSE)  
**Performance Goals**: Real-time domain stream; polling interval aligned with existing log stream (e.g. app.sse.poll-interval-ms)  
**Constraints**: Strict layered architecture (web → service → dao → kubernetes); Hubble client in dedicated package; no K8s API types outside kubernetes/  
**Scale/Scope**: One accessed-domains stream per image build; aggregation by distinct domain; replay on reconnect (FR-007)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|--------|
| **Tech stack** | OK | Java 21, Spring Boot 3.5.10, Gradle; gRPC/protobuf added for Hubble only |
| **Strict layered architecture** | OK | Controller → ImageBuildLogsService; new Hubble service used by pipeline steps via ImageDefinitionService (service layer); no web→dao or kubernetes types in web |
| **Transactional discipline** | OK | addAccessedDomains / repository updates in service/dao only |
| **Kubernetes isolation** | OK | Hubble gRPC is not K8s API; Hubble client lives in a dedicated package (e.g. `hubble` or under `service`), not in `kubernetes/` for Fabric8 types |
| **Observability** | OK | @LogExecution on new components; existing SSE/polling patterns |
| **Naming** | OK | *Service, *Controller, *Entity, *Dto, *Mapper per constitution |
| **API** | OK | SSE under `/api/v1/images/builds/{id}/accessed-domains` (or similar); ErrorView for errors |
| **Flyway** | OK | V1.50 per-vendor SQL migrations for `accessed_domains`; multi-vendor pattern followed |
| **MapStruct** | OK | DTO/entity mappers for AccessedDomain, ImageBuildDetailsDto extended |
| **Checkstyle / -Werror** | OK | All new code must pass |

No violations. Complexity tracking table not required.

## Project Structure

### Documentation (this feature)

```text
specs/004-cilium-domain-stream/
├── plan.md              # This file
├── research.md          # Phase 0: Hubble proto, Java codegen, log parsing
├── data-model.md        # Phase 1: AccessedDomain, entity/DTO propagation
├── quickstart.md        # Phase 1: dev/test runbook
├── contracts/           # Phase 1: SSE event shapes, API contract
└── tasks.md             # Phase 2 (/speckit.tasks) — NOT created by /speckit.plan
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── web/
│   ├── controller/
│   │   └── ImageBuildController.java          # + subscribeToAccessedDomains(id)
│   ├── dto/
│   │   ├── ImageBuildDetailsDto.java          # + accessedDomains
│   │   └── AccessedDomainDto.java (or inlined) # domain, verdict
│   └── mapper/
│       └── ImageBuildDetailsDtoMapper.java    # map accessedDomains
├── service/
│   ├── ImageBuildLogsService.java             # + streamAccessedDomains(id)
│   ├── ImageDefinitionService.java           # + addAccessedDomain(s); resetAccessedDomains
│   └── hubble/                                # or package of choice
│       └── HubbleRelayService.java            # gRPC client; stream GetFlows; parse DNS → domain+verdict
├── dao/
│   ├── entity/
│   │   └── ImageDefinitionEntity.java        # + accessedDomains List<AccessedDomain>
│   ├── repository/
│   │   └── ImageDefinitionRepository.java   # + addAccessedDomains
│   ├── jpa/
│   │   └── ImageDefinitionJpaRepository.java # (entity change only if no custom query)
│   └── mapper/
│       └── Persistence*Mapper.java            # map AccessedDomain ↔ persistence
├── model/
│   ├── ImageDefinition.java                  # + accessedDomains List<AccessedDomain>
│   └── AccessedDomain.java                   # domain (String), verdict (enum ALLOWED, BLOCKED)
src/main/resources/db/migration/
├── H2/V1.50__AddAccessedDomainsToImageDefinition.sql
├── POSTGRES/V1.50__AddAccessedDomainsToImageDefinition.sql
└── MS_SQL_SERVER/V1.50__AddAccessedDomainsToImageDefinition.sql
# Hubble generated code (protobuf):
build/generated/sources/proto/... (or src/main/proto + plugin) — see research.md
```

**Structure decision**: Single-module backend; new SSE endpoint and new Hubble client service; persistence follows existing image_definition + Flyway multi-vendor pattern.

## Complexity Tracking

Not applicable — no constitution violations.
