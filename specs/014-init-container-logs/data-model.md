# Data Model: Init Container Logs

## New Model: ContainerDetails

Read-only projection of Kubernetes container state. Not persisted.

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Container name from pod spec |
| `type` | `ContainerType` (enum: `INIT`, `WORKLOAD`, `SIDECAR`) | Container role: `INIT` = runs before startup; `WORKLOAD` = the main business-logic container (the log endpoint's default); `SIDECAR` = helper container (queue-proxy, istio-proxy, log shipper, or a K8s 1.29+ restartPolicy=Always init sidecar) |
| `state` | `String` (nullable) | Current state: `running`, `waiting`, `terminated`, or `null` if unknown |
| `stateReason` | `String` (nullable) | Reason for the current state (e.g., `CrashLoopBackOff`, `Completed`, `Error`) |

## Modified Model: PodInfo

Add a new field:

| Field | Type | Description |
|-------|------|-------------|
| `containers` | `List<ContainerDetails>` | All containers in the pod, ordered: init containers first, then regular (workload + sidecars) |

## New DTO: ContainerDetailsDto

Maps 1:1 from `ContainerDetails`.

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Container name |
| `type` | `String` | `INIT`, `WORKLOAD`, or `SIDECAR` |
| `state` | `String` (nullable) | Current state |
| `stateReason` | `String` (nullable) | State reason |

## Modified DTO: PodInfoDto

Add a new field:

| Field | Type | Description |
|-------|------|-------------|
| `containers` | `List<ContainerDetailsDto>` | All containers in the pod |

## Modified Interface: DeploymentManager

`getContainerResourceForLogs` signature change:

```
ContainerResource getContainerResourceForLogs(String id, String podName, boolean previous)
→
ContainerResource getContainerResourceForLogs(String id, String podName, String containerName, boolean previous)
```

`containerName` is nullable — when `null`, the current behavior is preserved (default container selection per deployment type).

## No Database Changes

This feature is read-only from Kubernetes state. No new tables, columns, or migrations.
