# Implementation Plan: Node Pool Selector

**Branch**: `016-node-pool-selector` | **Date**: 2026-04-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/016-node-pool-selector/spec.md`

## Summary

Add a node pool selector feature that lets administrators view available Kubernetes node pools with live resource utilization, assign a single pool to any deployment, and enforce hard node affinity at deploy time. This requires a new `/api/v1/node-pools` read endpoint backed by application configuration + live K8s node queries, extending the deployment data model with an optional `nodePool` field, and injecting `requiredDuringSchedulingIgnoredDuringExecution` node affinity into Knative, NIM, and KServe manifest generators.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2, Fabric8 Knative Client 7.5.2, MapStruct 1.6.0, Lombok 8.10, SpringDoc OpenAPI 2.8.5
**Storage**: H2 (dev/test), PostgreSQL, SQL Server — Flyway migrations, JPA `ddl-auto: validate`
**Testing**: JUnit 5 + AssertJ, Testcontainers 1.21.3, `./gradlew testFast` (H2), `./gradlew test` (full)
**Target Platform**: Linux server (Kubernetes cluster)
**Project Type**: Web service (Spring Boot)
**Performance Goals**: Live K8s API queries per request (no caching per clarification)
**Constraints**: All deployment types must support node pool affinity; hard affinity only
**Scale/Scope**: Node pool count in single digits (configuration-driven); node count per pool typically <50

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Layered architecture (web → service → dao/kubernetes) | PASS | NodePoolController → NodePoolService → K8sClient. Affinity injection in manifest generators (kubernetes layer) |
| `@Transactional` only on service/dao | PASS | No transactions needed for node pool read (config + live K8s). Deployment nodePool field persisted via existing DeploymentService transactional methods |
| K8s isolation in `kubernetes/` package | PASS | Node listing and pod resource queries added to K8sClient. Affinity injection in manifest generators |
| `@LogExecution` on all Spring components | PASS | Will add to new NodePoolController, NodePoolService |
| Naming conventions | PASS | NodePoolDto, NodePoolController, NodePoolService, NodePoolProperties |
| Code style (Google Java, 180-char, -Werror) | PASS | Enforced by Checkstyle |
| Config defaults in application.yml only | PASS | Node pool list config in application.yml; Java `@ConfigurationProperties` fields without initializers |
| Flyway owns schema | PASS | V1.57 migration for `node_pool` column across all 3 vendors |
| No business logic in entities | PASS | DeploymentEntity gets a plain `nodePool` String field |
| OpenAPI annotations on endpoints | PASS | `@Operation` + `@ApiResponse` on NodePoolController |
| `docs/configuration.md` updated | PENDING | Must add node pool config properties |

## Project Structure

### Documentation (this feature)

```text
specs/016-node-pool-selector/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 research output
├── data-model.md        # Phase 1 data model
├── quickstart.md        # Phase 1 quickstart
├── contracts/           # Phase 1 API contracts
│   └── node-pools-api.md
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── configuration/
│   └── NodePoolProperties.java              # NEW — @ConfigurationProperties for node pool list
├── web/
│   ├── controller/
│   │   └── NodePoolController.java          # NEW — GET /api/v1/node-pools
│   ├── dto/
│   │   └── nodepool/
│   │       ├── NodePoolDto.java             # NEW — response DTO
│   │       ├── NodeSpecDto.java             # NEW — per-node spec (cpu, mem, gpu)
│   │       └── NodeUtilizationDto.java      # NEW — per-node utilization snapshot
│   └── mapper/
│       └── NodePoolDtoMapper.java           # NEW — config + K8s data → DTO
├── service/
│   ├── nodepool/
│   │   └── NodePoolService.java             # NEW — orchestrates config + K8s queries
│   └── manifest/
│       ├── KnativeManifestGenerator.java    # MODIFIED — add affinity injection
│       ├── NimManifestGenerator.java        # MODIFIED — add affinity injection
│       └── InferenceManifestGenerator.java  # MODIFIED — add affinity injection
├── kubernetes/
│   └── K8sClient.java                       # MODIFIED — add listNodes, listPodsByNode methods
├── model/
│   └── deployment/
│       ├── Deployment.java                  # MODIFIED — add nodePool field
│       └── CreateDeployment.java            # MODIFIED — add nodePool field
├── dao/
│   └── entity/
│       └── deployment/
│           └── DeploymentEntity.java        # MODIFIED — add nodePool column
└── web/dto/deployment/
    ├── DeploymentDto.java                   # MODIFIED — add nodePool field
    └── CreateDeploymentRequestDto.java      # MODIFIED — add nodePool field

src/main/resources/
├── application.yml                          # MODIFIED — add app.node-pools config
└── db/migration/
    ├── H2/V1.57__AddNodePoolColumn.sql
    ├── POSTGRES/V1.57__AddNodePoolColumn.sql
    └── MS_SQL_SERVER/V1.57__AddNodePoolColumn.sql

src/test/java/com/epam/aidial/deployment/manager/
└── functional/h2/
    └── NodePoolFunctionalTest.java          # NEW — API tests
```

**Structure Decision**: All new code follows the existing layered architecture. Node pool is a cross-cutting concern on the deployment model (single field addition to existing hierarchy). The node pool listing is a standalone read flow (controller → service → K8s client). Affinity injection slots into the existing manifest generator pattern.

## Complexity Tracking

No constitution violations — no complexity justification needed.
