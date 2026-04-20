# Data Model: NIM KServe Migration & Configurable Storage Size

**Status**: Implemented

## Entity Changes

### NimDeployment (existing entity — modified)

| Field | Type | Nullable | Validation | Notes |
|-------|------|----------|------------|-------|
| containerGrpcPort | Integer | Yes | @Min(1) @Max(65535) | Existing field |
| **storageSize** | **String** | **Yes** | **@ValidStorageSize** | **NEW — Kubernetes quantity format (e.g., "20Gi", "500Mi", "21474836480")** |

### Database Migration V1.57

**Table**: `nim_deployment` (JOINED inheritance from `deployment`)

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| storage_size | VARCHAR(255) | Yes | NULL |

**Audit table**: `nim_deployment_aud` — same column added for Hibernate Envers audit trail.

Applied to all 3 database vendors: H2, PostgreSQL, SQL Server.

## Validation Rules

### StorageSize Validation (`@ValidStorageSize`)

| Rule | Behavior |
|------|----------|
| Null value | Accepted (field is optional) |
| Valid Kubernetes quantity | Accepted — binary suffixes (Ki, Mi, Gi, Ti, Pi, Ei), decimal suffixes (k, M, G, T), plain bytes |
| Zero or negative | Rejected — 400 "Storage size must be a positive value" |
| Unparseable string | Rejected — 400 "Invalid storage size" |
| Exceeds configured max | Rejected — 400 "Storage size must not exceed {max}" |

**Configurable upper bound**: `app.validation.resources.max-storage-size` (default: `200Gi`)

## Manifest Impact

When `storageSize` is non-null, it overrides `spec.storage.pvc.size` on the generated NIMService manifest. When null, the template default (20Gi from `application.yml`) is preserved.
