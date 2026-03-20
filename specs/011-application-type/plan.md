# Implementation Plan: Application Image Definition & Deployment Type

**Branch**: `011-application-type` | **Date**: 2026-03-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/011-application-type/spec.md`

## Summary

Add "APPLICATION" as a new image definition and deployment type, mirroring the existing Adapter type exactly. This involves creating parallel class hierarchies (model, DTO, entity), database migrations for new tables, registering the type in all polymorphic mappers/switches/enums, and adding the type to config import/export. No new API endpoints needed — the existing polymorphic endpoints handle the new type automatically.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: MapStruct 1.6.0, Lombok, Jackson 2.21.1, Fabric8 Knative Client 7.5.2
**Storage**: H2 (dev/test), PostgreSQL, SQL Server — Flyway 11.14.0 migrations
**Testing**: JUnit 5 + AssertJ, Testcontainers 1.21.3, H2 for `testFast`
**Target Platform**: Linux server (K8s deployment)
**Project Type**: Web service (Spring Boot REST API)
**Performance Goals**: Same as existing Adapter type — no new performance requirements
**Constraints**: Checkstyle (Google Java Style, 180 chars, `-Werror`), `ddl-auto: validate`
**Scale/Scope**: Minimal new code — all Application classes are empty marker subclasses mirroring Adapter

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Rule | Status | Notes |
|------|--------|-------|
| Strict Layered Architecture | PASS | New classes follow existing layer boundaries (web/service/dao) |
| Transactional Discipline | PASS | No new `@Transactional` annotations needed — parent classes handle it |
| Kubernetes Isolation | PASS | KnativeDeploymentManager is in `service/deployment/` (pre-existing pattern); change is adding a type to the supported list — no new K8s API usage |
| Observability First | PASS | `@LogExecution` already on all relevant Spring components — no new components needed |
| Security by Configuration | PASS | RBAC inherited from existing controller security annotations |
| Naming Conventions | PASS | Following established patterns: `Application*Entity`, `Application*Dto`, etc. |
| Code Style | PASS | Will run `checkstyleMain checkstyleTest` |
| MapStruct `componentModel = "spring"` | PASS | Existing mappers already configured — just add `@SubclassMapping` |
| Flyway-only migrations | PASS | New tables via Flyway SQL migrations |
| No business logic in entities | PASS | All new entity classes are empty marker subclasses |
| Configuration property defaults in YAML only | PASS | No new configuration properties needed |

**Post-Phase 1 re-check**: All gates still pass. No new patterns introduced.

## Project Structure

### Documentation (this feature)

```text
specs/011-application-type/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (no new endpoints — existing contracts unchanged)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Generic Specs (documentation updates)

The project maintains standalone domain specs in `specs/` that describe each type's behavior and API contract. This feature must:

1. **Create new type-specific specs** (mirroring `specs/adapter-*`):
   - `specs/application-deployments/spec.md` — Application deployment contract, source types, Knative requirement (mirrors `specs/adapter-deployments/spec.md`)
   - `specs/application-image-definitions/spec.md` — Application image definition contract, no additional fields beyond base (mirrors `specs/adapter-image-definitions/spec.md`)

2. **Update existing generic specs** to reference the new APPLICATION type:
   - `specs/deployments/spec.md` — Add APPLICATION to the list of image-based deployment types (currently lists MCP, Interceptor, Adapter) and update key terms/type references
   - `specs/image-definitions/spec.md` — Add APPLICATION to the list of subtypes (currently lists MCP, Interceptor, Adapter), update `$type` discriminator values and `ImageType` enum references
   - `specs/export-import/spec.md` — Add `APPLICATION_IMAGE_DEFINITION` and `APPLICATION_DEPLOYMENT` to `ExportConfigComponentType` enum, update image-based deployment lists
   - `specs/kubernetes-manifests/spec.md` — Add Application to the KNative-managed deployment type lists (currently "MCP, Interceptor, and Adapter") and related-specs links
   - `specs/image-builds/spec.md` — Add Application alongside Adapter/Interceptor in pipeline selection and build scenarios
   - `specs/buildkit/spec.md` — Add Application alongside Adapter/Interceptor in `ImageWrapperBuildPipeline` references and Docker-source copy scenarios
   - `specs/README.md` — Add entries for `application-image-definitions` and `application-deployments` to the spec index table

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── model/
│   ├── ApplicationImageDefinition.java          # NEW — extends ImageDefinition
│   ├── ImageDefinition.java                     # MODIFY — add @JsonSubTypes entry
│   ├── ImageType.java                           # MODIFY — add APPLICATION enum value + switch case
│   └── deployment/
│       ├── ApplicationDeployment.java            # NEW — extends Deployment
│       ├── CreateApplicationDeployment.java      # NEW — extends CreateDeployment
│       └── Deployment.java                       # MODIFY — add @JsonSubTypes entry
├── web/
│   ├── dto/
│   │   ├── ApplicationImageDefinitionDto.java            # NEW — extends ImageDefinitionDto
│   │   ├── ApplicationImageDefinitionRequestDto.java     # NEW — extends ImageDefinitionRequestDto
│   │   ├── ImageDefinitionDto.java                       # MODIFY — add @JsonSubTypes entry
│   │   ├── ImageDefinitionRequestDto.java                # MODIFY — add @JsonSubTypes entry
│   │   ├── DeploymentTypeDto.java                        # MODIFY — add APPLICATION enum value
│   │   └── deployment/
│   │       ├── ApplicationDeploymentDto.java             # NEW — extends ImageBasedDeploymentDto
│   │       ├── CreateApplicationDeploymentRequestDto.java # NEW — extends CreateImageBasedDeploymentRequestDto
│   │       ├── DeploymentDto.java                        # MODIFY — add @JsonSubTypes entry
│   │       └── CreateDeploymentRequestDto.java           # MODIFY — add @JsonSubTypes entry
│   └── mapper/
│       ├── ImageDefinitionDtoMapper.java          # MODIFY — add @SubclassMapping
│       └── DeploymentDtoMapper.java               # MODIFY — add @SubclassMapping
├── mapper/
│   └── DeploymentMapper.java                      # MODIFY — add @SubclassMapping
├── dao/
│   ├── entity/
│   │   ├── ApplicationImageDefinitionEntity.java  # NEW — extends ImageDefinitionEntity
│   │   └── deployment/
│   │       └── ApplicationDeploymentEntity.java   # NEW — extends DeploymentEntity
│   ├── mapper/
│   │   ├── PersistenceImageDefinitionMapper.java  # MODIFY — add @SubclassMapping
│   │   └── PersistenceDeploymentMapper.java       # MODIFY — add @SubclassMapping
│   └── repository/
│       ├── ImageDefinitionRepository.java         # MODIFY — add switch case
│       └── DeploymentRepository.java              # MODIFY — add switch case
├── service/
│   ├── deployment/
│   │   ├── KnativeDeploymentManager.java          # MODIFY — add ApplicationDeployment to supported list
│   │   └── DeploymentManagerProvider.java         # MODIFY — add switch case
│   ├── ImageBuildRunner.java                      # MODIFY — add instanceof ApplicationImageDefinition
│   └── config/
│       ├── ConfigExporter.java                    # MODIFY — add switch cases (addImageDef, addDeployment, getConfig)
│       ├── imports/
│       │   ├── DeploymentImporter.java            # MODIFY — add importMap call
│       │   └── ImageDefinitionImporter.java       # MODIFY — add importMap call
│       └── previews/
│           ├── ImageDefinitionImportPreviewer.java # MODIFY — add Application preview
│           └── DeploymentImportPreviewer.java     # MODIFY — add Application preview
├── web/
│   ├── dto/
│   │   ├── internal/
│   │   │   ├── ApplicationDeploymentInternalDto.java  # NEW — extends DeploymentInternalDto
│   │   │   └── DeploymentInternalDto.java             # MODIFY — add @JsonSubTypes entry
│   │   └── config/
│   │       ├── ExportConfigComponentTypeDto.java       # MODIFY — add APPLICATION enum values
│   │       └── ImportConfigPreviewDto.java             # MODIFY — add Application fields
│   ├── mapper/
│   │   ├── ExportConfigMapper.java                # MODIFY — add Application streams to preview
│   │   └── ImportConfigDtoMapper.java             # MODIFY — add Application mapping entries
│   └── validation/
│       └── ImportConfigValidator.java             # MODIFY — add Application validation calls
└── model/config/
    ├── ExportConfig.java                          # MODIFY — add application maps
    ├── ExportConfigComponentType.java             # MODIFY — add APPLICATION enum values
    └── ImportConfigPreview.java                   # MODIFY — add Application preview fields

src/main/resources/db/migration/
├── H2/V1.54__CreateApplicationTables.sql          # NEW
├── POSTGRES/V1.54__CreateApplicationTables.sql    # NEW
└── MS_SQL_SERVER/V1.54__CreateApplicationTables.sql # NEW

src/test/java/com/epam/aidial/deployment/manager/
├── functional/
│   ├── tests/DeploymentFunctionalTest.java        # MODIFY — add Application test methods
│   └── utils/FunctionalTestHelper.java            # MODIFY — add Application helper methods
```

**Structure Decision**: Existing single-project Spring Boot structure. New files follow established package layout — each new class mirrors its Adapter counterpart in the same package.

## Complexity Tracking

No constitution violations. All new code follows established patterns exactly.
