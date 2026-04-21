# Implementation Plan: Stop Image Build

**Branch**: `017-stop-image-build` | **Date**: 2026-04-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-stop-image-build/spec.md`

## Summary

Add an administrator-initiated action to interrupt an image build that is currently in the `BUILDING` state. The action deletes the backing Kubernetes Job (and its pods) synchronously; only after cluster-side cleanup confirms success does the `image_definition.build_status` advance to a new terminal value `BUILD_STOPPED`. If cluster cleanup fails, the entire stop action fails and the recorded status remains `BUILDING` (per Clarification Q1). Rejections for non-running builds (Q2) reuse the existing error shape but include the current recorded status in the payload so clients can reconcile without a follow-up request. The stop action takes no body — the target `imageDefinitionId` is the complete contract (Q5). Delete-while-building is explicitly out of scope (Q4).

Technical approach: a new `DELETE /api/v1/images/builds/{imageDefinitionId}` endpoint on the existing `ImageBuildController` flows into a thin orchestration service that verifies state, calls a new `kubernetes/` method to delete the Job synchronously, then calls a new conditional repository method that advances `BUILDING → BUILD_STOPPED` only if the row is still in `BUILDING`. The pipeline-runner thread's end-of-pipeline status updates are switched to the same conditional-update pattern so a pipeline returning from `waitJob` after a stop cannot overwrite `BUILD_STOPPED` with `BUILD_SUCCESSFUL`/`BUILD_FAILED`. SSE streams terminate automatically once `ImageStatus.BUILD_STOPPED.isFinal()` returns `true`.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.5.10, Spring Data JPA, Fabric8 Kubernetes Client 7.5.2, Flyway 11.14.0, MapStruct 1.6.0, Lombok 8.10, AssertJ, Mockito  
**Storage**: `image_definition.build_status` — `VARCHAR(32)` across H2, PostgreSQL, SQL Server (confirmed in `V1.27__AddVersioningAndRemoveImageEntity.sql` in each vendor migration directory); no CHECK constraint or native enum — adding `BUILD_STOPPED` requires **no Flyway migration**.  
**Testing**: `./gradlew testFast` (H2 + unit); `./gradlew test` includes PostgreSQL and SQL Server testcontainers. JUnit 5 + AssertJ + Mockito. Controller tests use `@WebMvcTest`; functional tests extend `H2FunctionalTests` / `PostgresFunctionalTests` / `SqlServerFunctionalTests`.  
**Target Platform**: Linux backend service; Kubernetes cluster with Fabric8 Client 7.5.2 managing BuildKit build Jobs.  
**Project Type**: Spring Boot web service (single module, layered: `web → service → dao / kubernetes`).  
**Performance Goals**:
- Stop action returns (with either `204` or an explicit failure) within the hard-coded `30`-second window (SC-001).
- Cluster-side resources are fully removed within 60s of a successful stop (SC-005).

**Constraints**:
- Strict layered architecture (Principle I): K8s API calls stay in `kubernetes/`.
- `@Transactional` only on service/dao layers (Principle II).
- `@LogExecution` on every new Spring component (Principle IV).
- All SSE continues via the existing `ImageBuildLogsService` polling model — no changes to that polling interval.
- Race safety between pipeline-runner thread and stop action must be deterministic via conditional status updates; no new in-process locks.

**Scale/Scope**:
- At most one active build per image definition (existing invariant — builds are keyed by `imageDefinitionId`).
- Stop action is admin-only and expected to be low-frequency (single-digit invocations per day in steady state). No caching / batching needed.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against `.specify/memory/constitution.md` v1.2.2:

| Rule | Status | Notes |
|---|---|---|
| Strict Layered Architecture (I) | PASS | New endpoint → new thin orchestration service (`service/` layer) → new `JobRunner.deleteJob` (`kubernetes/` layer) + new `ImageDefinitionService.stopBuild` (`service/` layer) → new `ImageDefinitionRepository.stopBuild` (`dao/` layer). No cross-layer shortcuts. |
| Transactional Discipline (II) | PASS | `@Transactional` added to `ImageDefinitionService.stopBuild` and to the repository wrapper method. Controller carries none. |
| Kubernetes Isolation (III) | PASS | All Fabric8 Job/Pod delete calls live in `kubernetes/JobRunner` (and possibly `KubernetesClient`), nowhere else. |
| Observability First (IV) | PASS | New orchestration service will be a `@Service` with `@LogExecution`. No new metrics introduced; status transition is already observable via existing SSE/audit. |
| Security by Configuration (V) | PASS | New endpoint carries `@FullAdminOnly`, matching `POST /api/v1/images/builds`. |
| Naming Conventions | PASS | Planned artifacts: `ImageBuildStopService`, `ImageBuildNotInProgressException`, `StopImageBuildFunctionalTest`, etc. |
| Code Style (180-char, `-Werror`, no wildcard imports, `CollectionUtils`/`StringUtils`) | PASS | Enforced by Checkstyle. |
| API Conventions | PASS | New endpoint under `/api/v1/images/builds/{id}` uses `DELETE`; response `204 No Content`; `ErrorView` error shape extended to include current recorded status for the FR-002 rejection case (see contract). |
| Testing Conventions | PASS | `shouldStopRunningBuild`, `shouldFailStopBuild_whenBuildIsNotInProgress`, etc. Functional tests across H2/Postgres/SQL Server. |
| Multi-Vendor DB | PASS | No migration needed (VARCHAR column with no CHECK constraint). The `PersistenceImageStatus` enum change maps 1:1 by name. |
| Configuration property defaults | N/A | Stop timeout is hard-coded to `30` in `application.yml` (`image-build-stop-timeout-sec: 30`) and not exposed as an env var — no per-operator tuning is offered, so `docs/configuration.md` carries no entry. |
| Anti-Patterns | PASS | No business logic in entities; no K8s calls outside `kubernetes/`; no generic `Exception` catches; no polling loop added (existing `waitJob` stays; we *invoke* `deletePods` synchronously via Fabric8). |
| spec-kit Workflow Rules | PASS | No per-feature checklist generated. |

**Gate result**: PASS. No violations, no entries in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/017-stop-image-build/
├── spec.md              # Feature specification (written by /speckit.specify, clarified by /speckit.clarify)
├── plan.md              # This file
├── research.md          # Phase 0 output — race handling, cleanup pathway, audit, exception shape
├── data-model.md        # Phase 1 output — enum + conditional state transitions
├── quickstart.md        # Phase 1 output — manual verification recipe
├── contracts/
│   └── stop-image-build.md   # DELETE /api/v1/images/builds/{id} contract
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created by this command)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── web/
│   ├── controller/
│   │   └── ImageBuildController.java                      # ADD DELETE endpoint
│   └── handler/
│       └── DefaultExceptionHandler.java                   # ADD handler for ImageBuildNotInProgressException → 400
├── service/
│   ├── ImageBuildStopService.java                         # NEW — orchestrates stop
│   ├── ImageBuildRunner.java                              # MODIFY — pipeline result writes go through conditional path
│   └── ImageDefinitionService.java                        # ADD stopBuild(UUID) + use conditional methods from repository
├── dao/
│   └── repository/
│       └── ImageDefinitionRepository.java                 # ADD stopBuild(UUID); MODIFY failBuild to short-circuit on BUILD_STOPPED (completeBuildSuccessfully stays unconditional — see data-model.md)
├── dao/entity/
│   └── PersistenceImageStatus.java                        # ADD BUILD_STOPPED
├── model/
│   └── ImageStatus.java                                   # ADD BUILD_STOPPED(true)
├── kubernetes/
│   └── JobRunner.java                                     # ADD deleteJob(groupId, namespace) — synchronous, waits for removal
└── exception/
    └── ImageBuildNotInProgressException.java              # NEW — custom RuntimeException

src/test/java/com/epam/aidial/deployment/manager/
├── web/controller/none/
│   └── ImageBuildControllerTest.java                      # EXTEND — DELETE endpoint cases
├── service/
│   ├── ImageBuildStopServiceTest.java                     # NEW
│   └── ImageBuildRunnerTest.java                          # EXTEND — pipeline-race safety
└── functional/
    ├── h2/StopImageBuildFunctionalTest.java               # NEW
    ├── postgres/StopImageBuildPostgresFunctionalTest.java # NEW (optional — verify VARCHAR value round-trips on Postgres)
    └── sqlserver/StopImageBuildSqlServerFunctionalTest.java # NEW (optional — verify VARCHAR value round-trips on SQL Server)
```

**Structure Decision**: Single Spring Boot module, using the existing strict layering (`web → service → dao / kubernetes`). All feature code goes into the existing packages — no new top-level package. The new exception lives under an `exception/` package consistent with other custom exceptions in the codebase (per existing pattern). Tests mirror the main tree as usual.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — this section intentionally empty.
