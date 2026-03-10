# API Contract Changes: Command and Args for All Deployment Types

**Date**: 2026-03-10
**Feature**: [spec.md](../spec.md)

## Overview

Two new optional string fields (`command`, `args`) are added to the base deployment request and response DTOs. These fields were previously available only on Inference deployment endpoints; they now appear on all deployment type endpoints.

## Changed Endpoints

All deployment CRUD endpoints are affected because `command` and `args` move to the base DTO level:

| Method | Path | Change |
|--------|------|--------|
| POST | `/api/v1/deployments` | `command` and `args` accepted in request body for all `$type` values |
| PUT | `/api/v1/deployments/{id}` | `command` and `args` accepted in request body for all `$type` values |
| GET | `/api/v1/deployments/{id}` | `command` and `args` returned in response for all deployment types |
| GET | `/api/v1/deployments` | `command` and `args` returned in list response items |

## Request Schema Change

### Before (Inference only)

```json
{
  "$type": "inference",
  "name": "my-inference",
  "modelFormat": "vllm",
  "source": { ... },
  "command": "python -m vllm.entrypoints.openai.api_server",
  "args": "--model /models/llama --max-model-len 4096"
}
```

### After (all types)

```json
{
  "$type": "mcp",
  "name": "my-mcp-server",
  "imageDefinitionId": "...",
  "command": "node server.js",
  "args": "--transport http --port 8080"
}
```

```json
{
  "$type": "adapter",
  "name": "my-adapter",
  "imageDefinitionId": "...",
  "command": "/usr/bin/adapter",
  "args": "--config /etc/adapter.yaml --mode production"
}
```

```json
{
  "$type": "interceptor",
  "name": "my-interceptor",
  "imageDefinitionId": "...",
  "command": "python main.py",
  "args": "--verbose"
}
```

```json
{
  "$type": "nim",
  "name": "my-nim",
  "command": "/opt/nim/start.sh",
  "args": "--gpu-memory-utilization 0.9"
}
```

## Field Specification

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `command` | String \| null | No | null | Container entrypoint override. Parsed using shell-like tokenization (supports quoted strings). When null, the container image's default `ENTRYPOINT` is used. |
| `args` | String \| null | No | null | Container arguments override. Parsed using shell-like tokenization. When null, the container image's default `CMD` is used. |

## Response Schema Change

```json
{
  "$type": "mcp",
  "name": "my-mcp-server",
  "command": "node server.js",
  "args": "--transport http --port 8080",
  "status": { ... },
  ...
}
```

When not set, `command` and `args` are `null` in the response.

## Backward Compatibility

- **Inference deployments**: No API contract change. The `command` and `args` fields remain in the same position in the JSON payload. The only difference is that they are now inherited from the base DTO rather than defined on the Inference-specific DTO. This is transparent to API consumers.
- **Other deployment types**: Adding new optional nullable fields is backward-compatible. Existing clients that don't send `command`/`args` will continue to work (fields default to null).

## Validation

- If `command` or `args` cannot be parsed (e.g., unmatched quotes), the API returns HTTP 400 with an error message: `"Cannot parse command/arguments: '<value>'"`.
- Empty string or blank values are treated as null (no override).

## Update Semantics

Full-replace: when updating a deployment, omitting `command`/`args` clears previously set values (consistent with existing PUT semantics for all deployment fields).
