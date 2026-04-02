# Implementation Plan: NIM Service URL Schema Prefix

**Branch**: `012-nim-url-schema` | **Date**: 2026-03-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/012-nim-url-schema/spec.md`

## Summary

NIM service URL resolution currently returns raw endpoint values from the Kubernetes NIMService CRD status, which may lack a schema prefix (`http://` or `https://`). This feature modifies `NimDeploymentManager.resolveServiceUrl()` to prepend the appropriate schema based on endpoint type (http for cluster-internal, https for external), with a configurable override via `NimDeployProperties.urlSchema`.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2, Lombok, Spring Configuration Properties
**Storage**: N/A (no database changes)
**Testing**: JUnit 5 + Mockito (unit), AssertJ assertions
**Target Platform**: Linux server (Spring Boot service in Kubernetes)
**Project Type**: Web service (Spring Boot backend)
**Performance Goals**: N/A (trivial string operation, no measurable impact)
**Constraints**: Must not break existing URL resolution; must handle already-prefixed URLs
**Scale/Scope**: Single method change + 1 new config property + tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Layered architecture | PASS | Changes are in `service/deployment/` (service layer) and `configuration/` — correct layers |
| Transactional discipline | PASS | No `@Transactional` changes |
| Kubernetes isolation | PASS | No new K8s API calls; only modifying how existing K8s data is post-processed |
| Observability | PASS | `@LogExecution` already on `NimDeploymentManager`; existing log statements updated |
| Security by configuration | PASS | New config property follows existing env var pattern |
| Naming conventions | PASS | `NimDeployProperties` (existing), no new classes |
| Code style | PASS | Will verify via `./gradlew checkstyleMain checkstyleTest` |
| Testing conventions | PASS | Unit tests in `NimDeploymentManagerTest` follow `shouldDoX` naming |
| Config property defaults | PASS | Default declared only in `application.yml` via `${K8S_NIM_DEPLOYMENT_URL_SCHEMA:}` |
| Config documentation | PASS | `docs/configuration.md` will be updated with new env var |

## Project Structure

### Documentation (this feature)

```text
specs/012-nim-url-schema/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (affected files)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── configuration/
│   └── NimDeployProperties.java          # Add urlSchema field
└── service/deployment/
    └── NimDeploymentManager.java         # Modify resolveServiceUrl()

src/main/resources/
└── application.yml                       # Add url-schema property

src/test/java/com/epam/aidial/deployment/manager/
└── service/deployment/
    └── NimDeploymentManagerTest.java     # Add/update resolveServiceUrl tests

docs/
└── configuration.md                      # Document new env var
```

**Structure Decision**: No new files or packages needed. All changes are modifications to existing files in their correct architectural layers.

## Complexity Tracking

No constitution violations. No complexity justifications needed.
