# Implementation Plan: Init Container Logs

**Branch**: `014-init-container-logs` | **Date**: 2026-04-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/014-init-container-logs/spec.md`

## Summary

The pod log endpoint only streams logs from the main container. When init containers fail, users without Kubernetes cluster access cannot diagnose root causes. This plan adds an optional `containerName` query parameter to the log endpoint and extends pod listing to expose container details (name, type, state) for discovery.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2, MapStruct 1.6.0, Lombok 8.10
**Storage**: N/A (read-only from Kubernetes API, no database changes)
**Testing**: JUnit 5, AssertJ, Mockito, `./gradlew testFast` for development
**Target Platform**: Linux server (Spring Boot web service)
**Project Type**: Web service (REST API)
**Performance Goals**: N/A (follows existing SSE streaming patterns)
**Constraints**: Full backward compatibility — existing clients must work unchanged
**Scale/Scope**: Small feature — ~10 files modified, ~3 new files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Layered Architecture | PASS | Controller → Service → Kubernetes. No shortcuts. |
| Transactional Discipline | PASS | No transactions needed (read-only K8s state) |
| Kubernetes Isolation | PASS | All K8s calls remain in service/deployment layer (via `k8sClient`) |
| Observability | PASS | `@LogExecution` on all modified Spring components |
| Naming Conventions | PASS | `ContainerDetails` (model), `ContainerDetailsDto` (DTO), `ContainerType` (enum) |
| Code Style | PASS | Google Java Style, 180-char lines, no wildcards |
| API Conventions | PASS | `@Operation` + `@ApiResponse` on modified endpoints |
| Testing | PASS | Unit tests for all deployment manager types |
| No DB changes | N/A | No migrations needed |

**Post-design re-check**: PASS — no violations introduced.

## Project Structure

### Documentation (this feature)

```text
specs/014-init-container-logs/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── pod-logs-endpoint.md
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (changes)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── model/
│   ├── PodInfo.java                          # MODIFY: add containers field
│   ├── ContainerDetails.java                 # NEW: container info model
│   └── ContainerType.java                    # NEW: INIT/WORKLOAD/SIDECAR enum
├── web/
│   ├── controller/DeploymentController.java  # MODIFY: add containerName param
│   └── dto/
│       ├── PodInfoDto.java                   # MODIFY: add containers field
│       └── ContainerDetailsDto.java          # NEW: container info DTO
├── web/mapper/
│   └── DeploymentDtoMapper.java              # MODIFY: add container mapping
├── service/deployment/
│   ├── DeploymentManager.java                # MODIFY: interface signature
│   ├── DeploymentLogsService.java            # MODIFY: pass containerName
│   └── AbstractDeploymentManager.java        # MODIFY: core logic changes
└── (no kubernetes/ changes — existing k8sClient.getPodResource().inContainer() is sufficient)

src/test/java/com/epam/aidial/deployment/manager/service/deployment/
├── DeploymentLogsServiceTest.java            # MODIFY: update for new param
├── NimDeploymentManagerTest.java             # MODIFY: add init container tests
├── KnativeDeploymentManagerTest.java         # MODIFY: add init container tests
└── InferenceDeploymentManagerTest.java       # MODIFY: add init container tests
```

## Implementation Design

### Change 1: Add `containerName` query parameter to log endpoint

**File**: `web/controller/DeploymentController.java` (line ~167)

Add `@RequestParam(value = "containerName", required = false) String containerName` to `subscribeToLogs`. Pass it through to `DeploymentLogsService.streamLogs`.

### Change 2: Thread `containerName` through service layer

**File**: `service/deployment/DeploymentLogsService.java` (line ~33)

Add `String containerName` parameter to `streamLogs`. Pass to `getContainerResourceForLogs`.

**File**: `service/deployment/DeploymentManager.java` (line ~48)

Change interface method:
```
ContainerResource getContainerResourceForLogs(String id, String podName, boolean previous, String containerName);
```

### Change 3: Core logic in AbstractDeploymentManager

**File**: `service/deployment/AbstractDeploymentManager.java`

**`getContainerResourceForLogs` (line ~536)**:
- Accept new `String containerName` parameter
- If `containerName` is non-null, use it directly (skip `getContainerName(pod)`)
- If `containerName` is null, fall back to existing `getContainerName(pod)` behavior

**`findContainerStatus` (line ~568)**:
- Search `pod.getStatus().getContainerStatuses()` first (existing)
- If not found, also search `pod.getStatus().getInitContainerStatuses()`
- Track whether the found container is an init container (for loggability rules)

**`assertContainerLoggable` (line ~557)**:
- Add awareness of init containers
- For init containers in terminated state with `previous=false`: allow (don't throw) — terminated is the normal final state for init containers
- For init containers in running state: allow (same as regular containers)
- For regular containers: existing behavior unchanged

### Change 4: Container discovery in pod listing

**New file**: `model/ContainerDetails.java`
```java
@Data @AllArgsConstructor @NoArgsConstructor
public class ContainerDetails {
    private String name;
    private ContainerType type;
    private String state;      // "running", "waiting", "terminated", or null
    private String stateReason; // nullable
}
```

**New file**: `model/ContainerType.java`
```java
public enum ContainerType { INIT, WORKLOAD, SIDECAR }
```

**New file**: `web/dto/ContainerDetailsDto.java`
```java
public record ContainerDetailsDto(
    @NotNull String name,
    @NotNull ContainerType type,
    String state,
    String stateReason
) {}
```

**Modified**: `model/PodInfo.java` — add `List<ContainerDetails> containers` field

**Modified**: `web/dto/PodInfoDto.java` — add `List<ContainerDetailsDto> containers` field

**Modified**: `web/mapper/DeploymentDtoMapper.java` — add `List<ContainerDetailsDto> toContainerDetailsDto(List<ContainerDetails>)` mapping

**Modified**: `service/deployment/AbstractDeploymentManager.java` `toPodInfo` method (line ~458):
- Build `List<ContainerDetails>` from pod spec + status:
  - Iterate `pod.getSpec().getInitContainers()` → `ContainerType.INIT`
  - Iterate `pod.getSpec().getContainers()` → `ContainerType.WORKLOAD` (name matches `getContainerName(pod)`) or `ContainerType.SIDECAR` (otherwise)
  - Iterate `pod.getSpec().getInitContainers()` → `ContainerType.SIDECAR` (if `restartPolicy=Always` — K8s 1.29 formal sidecar) or `ContainerType.INIT`
  - Match each with status from `initContainerStatuses` / `containerStatuses`
  - Extract state and reason from `ContainerState`
- Set on `PodInfo`

### Change 5: Update spec documentation

**File**: `specs/deployments/spec.md` — update the "Stream pod logs via SSE" requirement to document the new `containerName` parameter and init container support.

## Complexity Tracking

No constitution violations. No new complexity beyond what the feature requires.
