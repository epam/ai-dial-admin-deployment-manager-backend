# Implementation Plan: Unified Deployment Source Model

**Branch**: `003-unified-deployment-source` | **Date**: 2026-03-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-unified-deployment-source/spec.md`

## Summary

Unify deployment source handling across all deployment types by introducing a `Source` sealed interface hierarchy, moving source data into a single JSON column on the base `deployment` table, and adding direct `imageReference` support for Knative deployments (MCP, Adapter, Interceptor). This eliminates scattered image definition fields on the base deployment and separate source columns on NIM/Inference subtype tables.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: MapStruct 1.6.0, Lombok, Jackson 2.21.1, Fabric8 Knative Client 7.5.2, Flyway 11.14.0
**Storage**: H2 (dev/test), PostgreSQL, MS SQL Server — Flyway-managed migrations, JPA with `ddl-auto: validate`
**Testing**: JUnit 5 + AssertJ, Testcontainers 1.21.3, H2 for lightweight local tests
**Target Platform**: Linux server (Spring Boot web service)
**Project Type**: Web service (REST API backend)
**Performance Goals**: Standard web API latency; migration must handle existing deployment data without downtime beyond restart
**Constraints**: Multi-vendor database support (H2, PostgreSQL, MSSQL); intentional breaking API change (no backward compat shim)
**Scale/Scope**: ~73 files changed, 1048 additions, 383 deletions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Strict Layered Architecture | PASS | Source model lives in `model/deployment/`; DTOs in `web/dto/deployment/`; persistence in `dao/entity/deployment/`; service logic in `service/deployment/` |
| Transactional Discipline | PASS | No `@Transactional` on controllers; service-layer transaction boundaries maintained |
| Kubernetes Isolation | PASS | `KnativeDeploymentManager` is in `service/deployment/` but delegates to `K8sKnativeClient`; no Fabric8 imports in service layer |
| Observability First | PASS | `@LogExecution` maintained on all modified components |
| Security by Configuration | PASS | No security model changes |
| Naming Conventions | PASS | All new classes follow established patterns: `*Source` (domain), `Persistence*Source` (entity), `*SourceDto` / `*SourceRequestDto` (DTO), `*DtoMapper` (mapper) |
| Code Style | PASS | Google Java Style enforced; records used for immutable value objects; `var` for obvious types |
| Testing Conventions | PASS | Tests follow `should*()` / `shouldFail*_when*()` naming; functional tests cover all DB vendors |
| Multi-Vendor Database Pattern | PASS | Migration V1.50 uses Java-based migration with common base + vendor subclasses |
| Anti-Patterns | PASS | No business logic in entities; no silent exception swallowing; no K8s calls from service layer |

**Post-Phase 1 Re-check**: All gates remain PASS. The design uses established patterns (JSON column storage, sealed interface polymorphism, MapStruct `@SubclassMapping`, Java-based Flyway migration with common base).

## Project Structure

### Documentation (this feature)

```text
specs/003-unified-deployment-source/
├── plan.md              # This file
├── research.md          # Phase 0 output — design decisions and rationale
├── data-model.md        # Phase 1 output — entity/DTO/persistence model changes
├── quickstart.md        # Phase 1 output — developer onboarding guide
├── contracts/
│   └── deployment-source-api.md  # Phase 1 output — API contract changes
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── model/deployment/
│   ├── Source.java                    # NEW: sealed interface
│   ├── InternalImageSource.java       # NEW: record
│   ├── ImageReferenceSource.java      # NEW: record
│   ├── HuggingFaceSource.java         # RENAMED from InferenceDeploymentHuggingFaceSource
│   ├── NgcRegistrySource.java         # RENAMED from NimDeploymentNgcRegistrySource
│   ├── Deployment.java                # MODIFIED: source replaces image def fields
│   ├── CreateDeployment.java          # MODIFIED: same
│   ├── InferenceDeployment.java       # MODIFIED: source type changed to Source
│   └── NimDeployment.java             # MODIFIED: source type changed to Source
├── dao/entity/deployment/
│   ├── PersistenceSource.java         # NEW: sealed interface
│   ├── PersistenceInternalImageSource.java    # NEW
│   ├── PersistenceImageReferenceSource.java   # NEW
│   ├── PersistenceHuggingFaceSource.java      # NEW
│   ├── PersistenceNgcRegistrySource.java      # NEW
│   ├── DeploymentEntity.java          # MODIFIED: source JSON column
│   ├── InferenceDeploymentEntity.java # MODIFIED: source removed
│   └── NimDeploymentEntity.java       # MODIFIED: source removed
├── web/dto/deployment/
│   ├── DeploymentSourceDto.java                       # NEW: response interface
│   ├── InternalImageDeploymentSourceDto.java          # NEW: response record
│   ├── ImageReferenceDeploymentSourceDto.java         # NEW: response record
│   ├── CreateDeploymentSourceRequestDto.java          # NEW: request interface
│   ├── CreateInternalImageDeploymentSourceRequestDto.java  # NEW: request record
│   ├── CreateImageReferenceDeploymentSourceRequestDto.java # NEW: request record
│   ├── ImageBasedDeploymentDto.java   # MODIFIED: source replaces image def fields
│   └── CreateImageBasedDeploymentRequestDto.java  # MODIFIED: source field
├── web/mapper/
│   └── DeploymentDtoMapper.java       # MODIFIED: @AfterMapping for source
├── dao/mapper/
│   └── PersistenceDeploymentMapper.java   # MODIFIED: unified source mapping
├── dao/repository/
│   └── DeploymentRepository.java      # MODIFIED: updateImageDefinitionForDeployments
├── service/deployment/
│   ├── DeploymentService.java         # MODIFIED: validateSourceForDeploymentType, resolveImageDefinition
│   └── KnativeDeploymentManager.java  # MODIFIED: resolveImageName
├── configuration/
│   ├── JsonMapperConfiguration.java   # MODIFIED: InternalImageSourceExportMixIn
│   └── export/
│       └── InternalImageSourceExportMixIn.java  # NEW
└── service/config/
    └── ConfigExporter.java            # MODIFIED: export handling

src/main/java/db/migration/
├── common/
│   └── V1_50__UnifyDeploymentSourceBase.java  # NEW: abstract base migration
├── H2/
│   └── V1_50__UnifyDeploymentSource.java      # NEW: H2 migration
├── POSTGRES/
│   └── V1_50__UnifyDeploymentSource.java      # NEW: Postgres migration
└── MS_SQL_SERVER/
    └── V1_50__UnifyDeploymentSource.java      # NEW: MSSQL migration

src/test/java/...
├── dao/repository/DeploymentRepositoryTest.java           # MODIFIED
├── functional/tests/DeploymentFunctionalTest.java         # MODIFIED
├── functional/tests/ConfigExportImportFunctionalTest.java # MODIFIED
├── functional/tests/FullWorkflowFunctionalTest.java       # MODIFIED
├── service/deployment/KnativeDeploymentManagerTest.java   # MODIFIED
├── service/deployment/InferenceDeploymentManagerTest.java # MODIFIED
├── service/deployment/NimDeploymentManagerTest.java       # MODIFIED
└── web/controller/none/DeploymentControllerTest.java      # MODIFIED
```

**Structure Decision**: Existing Spring Boot layered architecture preserved. New files follow established package structure. No new packages or modules introduced.

## Complexity Tracking

No constitution violations to justify. All changes follow established patterns.
