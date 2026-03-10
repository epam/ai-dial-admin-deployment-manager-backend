# Implementation Plan: Support Command and Args for All Deployment Types

**Branch**: `002-deployment-command-args` | **Date**: 2026-03-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-deployment-command-args/spec.md`

## Summary

Consolidate `command` and `args` fields from the Inference-specific level to the base Deployment level, enabling all deployment types (MCP, Adapter, Interceptor, NIM, Inference) to support container entrypoint and argument overrides. This involves migrating fields across all layers (DTO → domain model → entity → DB), updating manifest generators (Knative, NIM) to apply command/args to container specs, and creating Flyway migrations to move existing Inference data to the base deployment table.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: MapStruct 1.6.0, Lombok, Fabric8 Kubernetes/Knative Client 7.5.2, Jackson 2.21.1
**Storage**: PostgreSQL (primary), H2 (dev/test), SQL Server (supported); Flyway 11.14.0 for migrations
**Testing**: JUnit 5, AssertJ, Testcontainers 1.21.3; functional tests per DB vendor
**Target Platform**: Linux server (Kubernetes cluster)
**Project Type**: Web service (Spring Boot REST API)
**Performance Goals**: N/A — this is a schema/field addition, no new performance-sensitive paths
**Constraints**: Multi-vendor DB support (H2, PostgreSQL, SQL Server); backward-compatible API
**Scale/Scope**: ~15 files modified, 3 new migration files (one per DB vendor), no new endpoints

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Strict Layered Architecture | PASS | Changes follow web → service → dao direction. No layer violations. |
| Transactional Discipline | PASS | No new `@Transactional` annotations needed; existing patterns preserved. |
| Kubernetes Isolation | PASS | Manifest generators remain in `service/manifest/` package; K8s types stay isolated. |
| Observability First | PASS | No new Spring components; existing `@LogExecution` annotations on affected classes. |
| Security by Configuration | PASS | No security changes; command/args are user-provided configuration, not secrets. |
| Naming Conventions | PASS | All artifacts follow existing `*Dto`, `*Entity`, `*Mapper`, `*Generator` patterns. |
| Code Style | PASS | Google Java Style enforced by Checkstyle; `-Werror` active. |
| API Conventions | PASS | Fields added to existing base DTOs; OpenAPI annotations auto-inherited. |
| Testing Conventions | PASS | Tests follow `shouldDoX()` / `shouldFailDoX_whenY()` naming; multi-vendor coverage. |
| Multi-Vendor Database | PASS | Migration files created for all 3 vendors (H2, POSTGRES, MS_SQL_SERVER). |
| Anti-Patterns | PASS | No business logic in entities; no exception swallowing; no layer violations. |

**Post-Phase 1 Re-check**: All gates remain PASS. Design adds fields to existing base classes and updates manifest generators — no architectural changes.

## Project Structure

### Documentation (this feature)

```text
specs/002-deployment-command-args/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api-changes.md   # API contract changes
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── web/
│   ├── dto/deployment/
│   │   ├── CreateDeploymentRequestDto.java          # ADD: command, args (String, @Nullable)
│   │   ├── DeploymentDto.java                       # ADD: command, args (String, @Nullable)
│   │   ├── CreateInferenceDeploymentRequestDto.java # REMOVE: command, args
│   │   └── InferenceDeploymentDto.java              # REMOVE: command, args
│   └── mapper/
│       └── DeploymentDtoMapper.java                 # UPDATE: mapping for base-level fields
├── model/deployment/
│   ├── Deployment.java                              # ADD: command, args (List<String>, @Nullable)
│   ├── CreateDeployment.java                        # ADD: command, args (List<String>, @Nullable)
│   ├── InferenceDeployment.java                     # REMOVE: command, args
│   └── CreateInferenceDeployment.java               # REMOVE: command, args
├── dao/
│   ├── entity/deployment/
│   │   ├── DeploymentEntity.java                    # ADD: command, args (List<String>, @JdbcTypeCode JSON)
│   │   └── InferenceDeploymentEntity.java           # REMOVE: command, args
│   └── mapper/
│       └── PersistenceDeploymentMapper.java         # UPDATE: base-level command/args mapping
├── mapper/
│   └── DeploymentMapper.java                        # UPDATE: remove Inference-specific handling if any
└── service/manifest/
    ├── KnativeManifestGenerator.java                # UPDATE: add command/args params to serviceConfig()
    └── NimManifestGenerator.java                    # UPDATE: add command/args params to serviceConfig()

src/main/resources/db/migration/
├── H2/V1.49__MoveCommandArgsToDeploymentTable.sql
├── POSTGRES/V1.49__MoveCommandArgsToDeploymentTable.sql
└── MS_SQL_SERVER/V1.49__MoveCommandArgsToDeploymentTable.sql

src/test/java/com/epam/aidial/deployment/manager/
├── service/manifest/
│   ├── KnativeManifestGeneratorTest.java            # ADD: command/args test cases
│   └── NimManifestGeneratorTest.java                # ADD: command/args test cases
└── functional/                                      # UPDATE: deployment CRUD tests with command/args
```

**Structure Decision**: Existing single-project Spring Boot structure. All changes are modifications to existing files plus 3 new migration files. No new packages or modules needed.

## Complexity Tracking

> No constitution violations detected. This table is intentionally empty.
