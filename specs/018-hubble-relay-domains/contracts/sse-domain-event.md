# Contract: SSE Domain Event

## Overview

A `domain` Server-Sent Event is emitted by both the build log stream and the deployment pod log stream when Hubble Relay is enabled and a new unique (domain, verdict) pair is captured for the current scope. Domain events are interleaved with existing `logs` and `status` events in the same SSE connection.

## Wire Format

```
event: domain
data: {"domain":"<fqdn>","verdict":"<ALLOWED|BLOCKED>"}

```

*(Blank line terminates each SSE frame as per the SSE spec.)*

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `domain` | `string` | Bare external FQDN (trailing `.` stripped). Example: `auth.docker.io` |
| `verdict` | `string` enum | `ALLOWED` — flow was forwarded by Cilium. `BLOCKED` — flow was dropped by Cilium policy. |

### Example Payloads

```
event: domain
data: {"domain":"auth.docker.io","verdict":"ALLOWED"}

event: domain
data: {"domain":"registry-1.docker.io","verdict":"ALLOWED"}

event: domain
data: {"domain":"internal-pypi.corp.example.com","verdict":"BLOCKED"}

```

## Endpoints

Domain events appear in these SSE streams:

| Endpoint | Scope | Replay |
|----------|-------|--------|
| `GET /api/v1/images/builds/{id}/logs` | Per build run (`imageDefinitionId`) | Replayed for completed builds |
| `GET /api/v1/deployments/{id}/pods/{podId}/logs` | Per deployment activation | Replayed on reconnect |

## Constraints

- No timestamp field in the event payload (FR-005). Chronological order during replay is managed server-side using the insertion-order `id` column.
- Domain names are deduplicated per scope by (domain, verdict) pair (FR-009); the live stream may transiently emit a duplicate before DB deduplication confirms, but replay is always deduplicated.
- Search-domain-qualified names (`.cluster.local`, `.svc.cluster.local`, `.internal.*`) are filtered before recording and are never emitted (FR-009).
- When Hubble Relay is disabled, no `domain` events are emitted (FR-010).

## Polling Behaviour

Both streams use a polling model to check for new domain entries (configurable `app.sse.poll-interval-ms`, default 1000 ms). The client receives domain events within the next poll cycle after the entry is persisted.
