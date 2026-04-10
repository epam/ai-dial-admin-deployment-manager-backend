# Implementation Plan: Add Version to McpRegistryRef

**Branch**: `016-mcp-registry-version` | **Date**: 2026-04-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/016-mcp-registry-version/spec.md`

## Summary

Add an optional `version` field to `McpRegistryRef` across all three architectural layers (DTO, Model, Persistence) so that operators can reference a specific published version of an MCP registry package, not just the package name. The field is purely informational metadata — no database migration is needed because `ExternalRegistryRef` is stored as JSON inside the existing `source` column. Jackson handles missing fields as `null` for backward compatibility.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: MapStruct 1.6.0, Lombok 8.10, Jackson 2.21.1
**Storage**: JSON column (`source`) in `image_definition` and `deployment` tables — H2 (dev), PostgreSQL, SQL Server
**Testing**: JUnit 5 + AssertJ, Testcontainers 1.21.3, `./gradlew testFast` (H2), `./gradlew test` (full)
**Target Platform**: Linux server (Spring Boot web service)
**Project Type**: Web service (REST API)
**Performance Goals**: N/A — informational metadata field, no performance impact
**Constraints**: Google Java Style (180 chars), `-Werror`, Checkstyle 10.21.4
**Scale/Scope**: Minimal — adding one optional field to one subtype across 3 layers (3 records + tests)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Layered architecture (web → service → dao) | PASS | Changes touch only records/DTOs in each layer — no cross-layer violation |
| `@LogExecution` on Spring components | N/A | No new Spring components introduced |
| MapStruct `componentModel = "spring"` | PASS | Existing mappers already compliant; no new mappers needed (MapStruct auto-maps matching field names) |
| Naming conventions (`*Dto`, `Persistence*`) | PASS | Existing naming preserved |
| Checkstyle (Google Java Style, 180 chars) | PASS | Will verify with `./gradlew checkstyleMain checkstyleTest` |
| `-Werror` compilation | PASS | Will verify with `./gradlew clean build` |
| No `@Transactional` on controllers | N/A | No controller changes |
| Kubernetes isolation | N/A | No K8s changes |
| Flyway owns schema / `ddl-auto: validate` | PASS | No migration needed — JSON column |
| Anti-patterns check | PASS | No business logic in entities, no exception swallowing |

**Post-Phase 1 re-check**: All gates still PASS. Design adds only a field to existing records.

## Project Structure

### Documentation (this feature)

```text
specs/016-mcp-registry-version/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── mcp-registry-ref-version.md
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (files modified)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── web/dto/McpRegistryRefDto.java              # Add optional version field
├── model/McpRegistryRef.java                   # Add optional version field
├── dao/entity/PersistenceMcpRegistryRef.java   # Add optional version field

src/test/java/com/epam/aidial/deployment/manager/
├── functional/tests/ImageDefinitionFunctionalTest.java  # Add version test scenarios
├── functional/tests/DeploymentFunctionalTest.java       # Add version test scenarios
├── functional/tests/ConfigExportImportFunctionalTest.java # Verify version in export/import
```

**Structure Decision**: No new files or directories in source code. Only field additions to 3 existing records and test updates.

## Complexity Tracking

No constitution violations. No complexity justifications needed.
