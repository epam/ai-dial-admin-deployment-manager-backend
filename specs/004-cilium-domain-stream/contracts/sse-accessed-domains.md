# Contract: SSE Accessed Domains Stream

**Feature**: 004-cilium-domain-stream  
**Date**: 2025-03-13

## Endpoint

- **Method / path**: `GET /api/v1/images/builds/{id}/accessed-domains`
- **Path parameter**: `id` — UUID of the image definition (same as for logs/status).
- **Produces**: `text/event-stream` (SSE).
- **Authentication**: Same as existing image build endpoints (e.g. OIDc/basic per constitution).

## Event Types

The stream sends Server-Sent Events with the following event names and payloads.

### 1. `accessed-domains`

Sent when one or more domain entries are available (new or replayed). Each event carries a single domain entry so that clients can display entries incrementally.

**Payload**: JSON object per event.

| Field   | Type   | Description |
|---------|--------|-------------|
| domain  | string | The domain that was accessed (e.g. from DNS query). |
| verdict | string | `"ALLOWED"` or `"BLOCKED"`. |

**Example**:
```json
{ "domain": "github.com", "verdict": "ALLOWED" }
```

**Example**:
```json
{ "domain": "pypi.org", "verdict": "BLOCKED" }
```

### 2. `status`

Sent when build status changes (same semantics as log stream). Enables the client to know when the build has reached a final state and the stream will close.

**Payload**: string — build status name (e.g. `BUILDING`, `BUILD_SUCCESSFUL`, `BUILD_FAILED`).

### 3. Stream completion and errors

- **Normal completion**: After a `status` event with a final status (e.g. `BUILD_SUCCESSFUL`, `BUILD_FAILED`), the server closes the SSE stream. If the build ended with zero domains accessed, the server sends an explicit “no domain access” indication before closing (per FR-009) — e.g. a dedicated event or a final `accessed-domains` payload indicating empty.
- **Error (e.g. Cilium/Hubble unavailable)**: The server closes the stream with an error (e.g. SSE comment or final event with error payload) so the client can show an error state (FR-008).

## Reconnect / replay

On reconnect, the server resends all domain entries already persisted for that build (replay), then continues with new entries. Order of entries is not guaranteed (spec).

## Comparison with logs stream

- **Logs**: `GET /api/v1/images/builds/{id}/logs` — events `logs` (log line) and `status`.
- **Accessed domains**: `GET /api/v1/images/builds/{id}/accessed-domains` — events `accessed-domains` (domain + verdict) and `status`.  
Same polling/replay pattern as logs; separate stream and endpoint so that domain data and build logs do not interleave.
