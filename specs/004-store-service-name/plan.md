# Implementation Plan: Store Deployment Service Name

**Branch**: `004-store-service-name` | **Date**: 2026-03-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-store-service-name/spec.md`

## Summary

Persist the Kubernetes service name in the `deployment` table so that deployments remain associated with their K8s resources even when `resourceNamePrefix` changes. Replace derived naming (`K8sNamingUtils.generateMcpPrefixedName`/`generateName` + `IdExtractor`) with stored lookups. Unify new deployment naming under `generateName` for all types. Backfill existing active deployments via a Java Flyway migration that reads `RESOURCE_NAME_PREFIX` from the environment.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 K8s/Knative 7.5.2, Flyway 11.14.0, MapStruct 1.6.0, Lombok
**Storage**: H2 (dev/test), PostgreSQL 42.7.8, SQL Server 13.2.1 — multi-vendor via `DATASOURCE_VENDOR`
**Testing**: JUnit 5, AssertJ, Testcontainers 1.21.3; `./gradlew testFast` for dev
**Target Platform**: Linux server (K8s)
**Project Type**: Web service (Spring Boot)
**Performance Goals**: N/A — this is a correctness/resilience change, not performance-critical
**Constraints**: Migration must be backward-compatible; service name column nullable for NOT_DEPLOYED/STOPPED deployments
**Scale/Scope**: ~5 deployment types, ~3 deployment managers, ~3 event handlers, 1 new DB column

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Strict Layered Architecture | PASS | Changes follow web→service→dao→kubernetes flow. New DB column in dao, service name generation in service/kubernetes layers. |
| II. Transactional Discipline | PASS | Service name persisted within existing `@Transactional` deploy/save flows. No new transactions on controllers. |
| III. Kubernetes Isolation | PASS | K8s API calls remain in `kubernetes/` package. Event handlers updated in `kubernetes/informer/handler/`. |
| IV. Observability First | PASS | `@LogExecution` maintained on all modified components. |
| V. Security by Configuration | PASS | No security changes. |
| Naming Conventions | PASS | New column follows existing entity field patterns. |
| Code Style | PASS | Google Java Style, 180 char line length, `-Werror`. |
| Testing Conventions | PASS | Functional tests will cover migration + service name flow for H2. |
| Multi-Vendor Database | PASS | Migration created for all 3 vendors (H2, Postgres, SQL Server). |
| Anti-Patterns | PASS | No violations. |

## Project Structure

### Documentation (this feature)

```text
specs/004-store-service-name/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── dao/
│   ├── entity/deployment/DeploymentEntity.java          # ADD serviceName field
│   ├── jpa/DeploymentJpaRepository.java                 # ADD findByServiceName, updateServiceName queries
│   ├── repository/DeploymentRepository.java             # ADD getByServiceName, updateServiceName wrappers
│   └── mapper/PersistenceDeploymentMapper.java          # MAP serviceName field
├── service/deployment/
│   ├── AbstractDeploymentManager.java                   # MOVE getServiceName here (reads stored serviceName), generate + persist in deploy()
│   ├── DeploymentService.java                           # PRESERVE existing serviceName on updateDeployment()
│   ├── KnativeDeploymentManager.java                    # REMOVE getServiceName override (now inherited)
│   ├── NimDeploymentManager.java                        # REMOVE getServiceName override (now inherited)
│   └── InferenceDeploymentManager.java                  # REMOVE getServiceName override (now inherited)
├── service/manifest/
│   ├── KnativeManifestGenerator.java                    # ACCEPT serviceName parameter for K8s resource naming
│   ├── NimManifestGenerator.java                        # ACCEPT serviceName parameter for K8s resource naming
│   └── InferenceManifestGenerator.java                  # ACCEPT serviceName parameter for K8s resource naming
├── kubernetes/informer/
│   ├── handler/AbstractResourceEventHandler.java        # CHANGE to look up by service name instead of IdExtractor
│   ├── handler/KnativeServiceEventHandler.java          # REMOVE IdExtractor dependency
│   ├── handler/NimServiceEventHandler.java              # REMOVE IdExtractor dependency
│   └── handler/InferenceServiceEventHandler.java        # REMOVE IdExtractor dependency
├── specification/CiliumNetworkPolicyCreator.java        # USE stored serviceName for policy naming
├── configuration/export/DeploymentExportMixIn.java      # EXCLUDE serviceName from config export
├── mapper/DeploymentMapper.java                         # IGNORE serviceName in toDeployment (defaults to null)
├── cleanup/resource/DisposableResourceManager.java      # USE stored serviceName (accept as parameter, remove generateServiceName)
└── utils/K8sNamingUtils.java                            # REMOVE extractMcpPrefixedId, extractId, generateMcpPrefixedName

src/main/resources/db/migration/
├── H2/V1.52__AddServiceNameColumn.sql                   # DDL: add column + unique index
├── POSTGRES/V1.52__AddServiceNameColumn.sql             # DDL: add column + unique index
└── MS_SQL_SERVER/V1.52__AddServiceNameColumn.sql        # DDL: add column + filtered unique index

src/main/java/db/migration/
├── common/V1_53__BackfillServiceNameBase.java           # Base class for backfill logic (reads RESOURCE_NAME_PREFIX env var)
├── H2/V1_53__BackfillServiceName.java                   # H2-specific backfill
├── POSTGRES/V1_53__BackfillServiceName.java             # Postgres-specific backfill
└── MS_SQL_SERVER/V1_53__BackfillServiceName.java        # SQL Server-specific backfill

src/test/java/com/epam/aidial/deployment/manager/
└── functional/                                          # UPDATE existing tests to verify service name persistence
```

**Structure Decision**: Standard single-project Spring Boot structure. All changes are within the existing package hierarchy. No new packages needed.

## Complexity Tracking

No constitution violations to justify.
