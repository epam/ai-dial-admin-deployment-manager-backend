# Contract: Pod Logs Endpoint

## Modified Endpoint: Stream Pod Logs

`GET /api/v1/deployments/{id}/pods/{podId}/logs`

### New Query Parameter

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `containerName` | `String` | No | (deployment-type default) | Name of the container to stream logs from. When omitted, uses the default container for the deployment type (preserving backward compatibility). |

### Existing Query Parameters (unchanged)

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `sinceTime` | `Instant` | No | — |
| `sinceSeconds` | `Integer` | No | — |
| `tail` | `Integer` | No | — |
| `previous` | `boolean` | No | `false` |

### Response

Unchanged — SSE stream with `name: logs` events.

### Error Cases

| Condition | Status | Message |
|-----------|--------|---------|
| Container name not found in pod | 404 | `Container is not found for deployment '{id}'` |
| Container not loggable (not running, no terminated state) | 400 | `Container is not ready for log streaming for deployment '{id}'` |

## Modified Endpoint: List Pods

`GET /api/v1/deployments/{id}/pods`

### Response Change

`PodInfoDto` gains a new field `containers`:

```json
[
  {
    "name": "pod-abc-123",
    "createdAt": "2026-04-10T12:00:00Z",
    "restartCount": 0,
    "lastTerminationReason": null,
    "lastExitCode": null,
    "lastSignal": null,
    "lastFinishedAt": null,
    "containers": [
      {
        "name": "init-download-model",
        "type": "INIT",
        "state": "terminated",
        "stateReason": "Completed"
      },
      {
        "name": "nim-llm",
        "type": "WORKLOAD",
        "state": "running",
        "stateReason": null
      },
      {
        "name": "queue-proxy",
        "type": "SIDECAR",
        "state": "running",
        "stateReason": null
      }
    ]
  }
]
```

Same change applies to `GET /api/v1/deployments/{id}/active-pods`.

### Container type semantics

| Type | Meaning |
|------|---------|
| `INIT` | Runs to completion before the pod starts (e.g., download model, schema init) |
| `WORKLOAD` | The main business-logic container — this is the container the log endpoint streams by default when `containerName` is omitted. Exactly one per pod. |
| `SIDECAR` | Helper container running alongside the workload (e.g., Knative `queue-proxy`, service-mesh proxies, log shippers). Also covers K8s 1.29+ `initContainers` with `restartPolicy: Always`. |

Consumers should use `WORKLOAD` to decide which container's logs to show first by default.
