# Data Model: Support Command and Args for All Deployment Types

**Date**: 2026-03-10
**Feature**: [spec.md](spec.md)

## Entity Changes

### DeploymentEntity (base) — MODIFIED

| Field | Type | Nullable | Storage | Change |
|-------|------|----------|---------|--------|
| command | List\<String\> | Yes | JSONB (PostgreSQL/H2), NVARCHAR(MAX) (SQL Server) | **ADD** |
| args | List\<String\> | Yes | JSONB (PostgreSQL/H2), NVARCHAR(MAX) (SQL Server) | **ADD** |

**Annotations**: `@JdbcTypeCode(SqlTypes.JSON)` on both fields (matches existing pattern in `InferenceDeploymentEntity`).

**Serialized format**: JSON array of strings, e.g. `["python", "-m", "server"]`

### InferenceDeploymentEntity — MODIFIED

| Field | Type | Change |
|-------|------|--------|
| command | List\<String\> | **REMOVE** (moved to base) |
| args | List\<String\> | **REMOVE** (moved to base) |

## Domain Model Changes

### Deployment (base) — MODIFIED

| Field | Type | Nullable | Change |
|-------|------|----------|--------|
| command | List\<String\> | Yes | **ADD** |
| args | List\<String\> | Yes | **ADD** |

### CreateDeployment (base) — MODIFIED

| Field | Type | Nullable | Change |
|-------|------|----------|--------|
| command | List\<String\> | Yes | **ADD** |
| args | List\<String\> | Yes | **ADD** |

### InferenceDeployment — MODIFIED

| Field | Type | Change |
|-------|------|--------|
| command | List\<String\> | **REMOVE** (inherited from base) |
| args | List\<String\> | **REMOVE** (inherited from base) |

### CreateInferenceDeployment — MODIFIED

| Field | Type | Change |
|-------|------|--------|
| command | List\<String\> | **REMOVE** (inherited from base) |
| args | List\<String\> | **REMOVE** (inherited from base) |

## DTO Changes

### CreateDeploymentRequestDto (base) — MODIFIED

| Field | Type | Nullable | Validation | Change |
|-------|------|----------|------------|--------|
| command | String | Yes | Parseable by CommandLineUtils | **ADD** |
| args | String | Yes | Parseable by CommandLineUtils | **ADD** |

### DeploymentDto (base) — MODIFIED

| Field | Type | Nullable | Change |
|-------|------|----------|--------|
| command | String | Yes | **ADD** |
| args | String | Yes | **ADD** |

### CreateInferenceDeploymentRequestDto — MODIFIED

| Field | Type | Change |
|-------|------|--------|
| command | String | **REMOVE** (inherited from base) |
| args | String | **REMOVE** (inherited from base) |

### InferenceDeploymentDto — MODIFIED

| Field | Type | Change |
|-------|------|--------|
| command | String | **REMOVE** (inherited from base) |
| args | String | **REMOVE** (inherited from base) |

## Database Migration

### V1.49__MoveCommandArgsToDeploymentTable.sql

**PostgreSQL/H2**:
1. `ALTER TABLE deployment ADD COLUMN command JSONB`
2. `ALTER TABLE deployment ADD COLUMN args JSONB`
3. `UPDATE deployment SET command = i.command, args = i.args FROM inference_deployment i WHERE deployment.id = i.id`
4. `ALTER TABLE inference_deployment DROP COLUMN command`
5. `ALTER TABLE inference_deployment DROP COLUMN args`

**SQL Server**:
1. `ALTER TABLE deployment ADD command NVARCHAR(MAX)`
2. `ALTER TABLE deployment ADD args NVARCHAR(MAX)`
3. `UPDATE d SET d.command = i.command, d.args = i.args FROM deployment d INNER JOIN inference_deployment i ON d.id = i.id`
4. `ALTER TABLE inference_deployment DROP COLUMN command`
5. `ALTER TABLE inference_deployment DROP COLUMN args`

## Type Conversion Flow

```
API Request (String)
  ↓ DeploymentDtoMapper.stringToList()
Domain Model (List<String>)
  ↓ PersistenceDeploymentMapper (direct mapping)
Entity (List<String>) → DB (JSONB array)

DB (JSONB array) → Entity (List<String>)
  ↓ PersistenceDeploymentMapper (direct mapping)
Domain Model (List<String>)
  ↓ DeploymentDtoMapper.listToString()
API Response (String)
```

**Conversion logic**: `CommandLineUtils.parseCommandline()` for String→List (shell-like tokenization with quote handling); `CommandLineUtils.quoteArgument()` + join for List→String.
