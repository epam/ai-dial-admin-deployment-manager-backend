# Contract: Stop Image Build

## Endpoint

```
DELETE /api/v1/images/builds/{imageDefinitionId}
```

### Path parameters

| Name | Type | Required | Description |
|---|---|---|---|
| `imageDefinitionId` | UUID (string, canonical lowercase hex with dashes) | yes | The image definition whose in-progress build is to be stopped. |

### Request body

**None.** The endpoint MUST reject any body with HTTP 400 via the existing request-parsing layer (per spec Clarification Q5: the image definition identifier is the complete contract).

### Authentication / Authorization

- Requires an authenticated principal (OIDC / basic / JWT per the configured security mode).
- Requires the **full admin** role — same authorization as `POST /api/v1/images/builds`. Wired via `@FullAdminOnly` on the controller method.
- Non-privileged callers receive `403 Forbidden` via the existing security layer (no custom handler needed).

### Responses

| Status | When | Body |
|---|---|---|
| `204 No Content` | Build was in progress, cluster-side cleanup succeeded, status advanced to `BUILD_STOPPED`. | empty |
| `400 Bad Request` | Build is not in progress (terminal state already, or image definition has never been built), **or** cluster-side deletion failed. | `ErrorView` (see below) |
| `403 Forbidden` | Caller is authenticated but lacks the full-admin role. | standard security `ErrorView` |
| `404 Not Found` | `imageDefinitionId` does not correspond to an existing image definition. | `ErrorView` |
| `500 Internal Server Error` | Unexpected error not covered above. | `ErrorView` |

### Success response (`204 No Content`)

- Body: empty.
- Side effects that MUST have happened before `204` is returned:
  1. The Kubernetes Job backing the build (and its pods, via foreground propagation) is gone from the API server.
  2. `image_definition.build_status` is `BUILD_STOPPED` for the targeted `imageDefinitionId`.
  3. Adjacent disposable resources are marked for cleanup via `DisposableResourceManager.markResourcesForCleanupByGroupId(...)` (reclaimed by the existing scheduled cleaner on its normal cadence).
  4. Any open SSE subscribers to `/status` and `/logs` for this build have observed the terminal-status event and their streams are closed (driven by the existing poll loop, not directly by this handler).
  5. The action is recorded in the existing audit log (see FR-011 / Phase 0 R3).

### Error response — `ErrorView` schema

The existing project-wide `ErrorView` (see constitution §API Conventions):

```json
{
  "path": "string",
  "method": "string",
  "status": 400,
  "error": "string",
  "message": "string",
  "traceparent": "string"
}
```

### Error — build not in progress (HTTP 400)

Triggered when:
- The image definition exists but its `buildStatus` is `NOT_BUILT`, `BUILD_SUCCESSFUL`, `BUILD_FAILED`, or already `BUILD_STOPPED`.
- A stop is accepted into the orchestration but at the conditional-update step 0 rows are affected (i.e., the pipeline-runner thread won the race and advanced the status already — per spec Clarification Q2).

Response body:

```json
{
  "path":   "/api/v1/images/builds/{imageDefinitionId}",
  "method": "DELETE",
  "status": 400,
  "error":  "Bad Request",
  "message": "Image build is not in progress (current status: <STATUS>)",
  "traceparent": "00-<trace>-<span>-01"
}
```

Where `<STATUS>` is one of `NOT_BUILT`, `BUILD_SUCCESSFUL`, `BUILD_FAILED`, `BUILD_STOPPED`. **This token MUST match the regex `\(current status: (NOT_BUILT|BUILDING|BUILD_SUCCESSFUL|BUILD_FAILED|BUILD_STOPPED)\)`** — consumed by the UI to reconcile its cached view without a follow-up GET.

Note: `BUILDING` is listed in the regex alternation for forward compatibility (it could appear if the race condition chain grows more states), but this endpoint never emits `BUILDING` as the current status in a rejection — a `BUILDING` row is the success path.

### Error — cluster-side deletion failed (HTTP 400)

Triggered when `JobRunner.deleteJob(...)` either returns an error from the Kubernetes API or the delete-confirmation wait times out.

Response body:

```json
{
  "path":   "/api/v1/images/builds/{imageDefinitionId}",
  "method": "DELETE",
  "status": 400,
  "error":  "Bad Request",
  "message": "Image build could not be stopped: <underlying cause>; build remains in BUILDING and may be retried",
  "traceparent": "00-<trace>-<span>-01"
}
```

The recorded status MUST remain `BUILDING` in this case (spec Clarification Q1). The caller is expected to retry.

### Error — image definition not found (HTTP 404)

Standard `ErrorView` with `status = 404`, `message = "Image definition '<id>' not found"` — produced by the existing `ImageDefinitionService.getImageDefinition(id)` path. No new handler.

## Idempotency semantics

- Two stop requests in quick succession for the same build: the first that wins the `BUILDING` → `BUILD_STOPPED` conditional update returns `204`; any subsequent stop request reads the now-terminal status and is rejected with the "not in progress (current status: BUILD_STOPPED)" 400 (FR-012).
- **Net effect is idempotent** (no additional cluster-side deletions, no additional status mutations) but **HTTP-level idempotent** is not claimed: the first 204 and the second 400 are distinct. This is intentional — idempotency at the HTTP layer would require swallowing "already stopped" into 204 (spec Clarification Q2 explicitly rejected that).

## Contract tests (to be written in Phase 2)

The following cases are the contract-test surface and MUST be exercised in `ImageBuildControllerTest` and/or a functional test:

1. **Happy path**: Given `buildStatus = BUILDING`, DELETE returns `204` with empty body; DB row flips to `BUILD_STOPPED`.
2. **Not in progress**: For each of `NOT_BUILT`, `BUILD_SUCCESSFUL`, `BUILD_FAILED`, `BUILD_STOPPED`, DELETE returns `400` with `message` matching the regex above and with the correct `<STATUS>` token; DB row is untouched.
3. **Not found**: DELETE against an unknown UUID returns `404`; no side effects.
4. **Forbidden**: Non-admin authenticated caller receives `403`; DB row untouched.
5. **Unauthenticated**: Anonymous caller receives `401` (existing security layer behavior).
6. **Request body rejected**: DELETE with any JSON body returns `400` (existing request-parsing behavior) and performs no side effects.
7. **Cluster-side deletion failed**: `JobRunner.deleteJob` throws a simulated `KubernetesClientException`; DELETE returns `400` with the "could not be stopped" message; DB row remains `BUILDING`; no `BUILD_STOPPED` audit event emitted.
8. **Rebuild after stop**: After a successful stop, `POST /api/v1/images/builds` for the same `imageDefinitionId` is accepted (FR-007).
