# Data Model: Auditing

## New Entities

### AuditRevisionEntity

Custom Envers revision entity mapped to `revinfo` table. Extends the default Envers revision metadata with author identity from Spring Security.

| Column      | Type         | Constraints              | Description                            |
|-------------|--------------|--------------------------|----------------------------------------|
| `id`        | INTEGER      | PK, auto-increment       | Revision number                        |
| `timestamp` | BIGINT       | NOT NULL                 | Transaction timestamp (epoch millis)   |
| `author`    | VARCHAR(255) |                          | Username from security context; `"unknown"` for unauthenticated HTTP requests; `"system"` for system operations (no security context) |
| `email`     | VARCHAR(320) |                          | Email from security context (null for unknown/system authors) |

**Relationships**: One-to-many to `AuditActivityEntity` (cascade ALL, orphan removal).

**Annotations**: `@RevisionEntity(AuditRevisionListener.class)`, `@RevisionNumber` on id, `@RevisionTimestamp` on timestamp.

---

### AuditActivityEntity

Denormalized activity feed mapped to `audit_activity` table. One record per tracked change event, linked to a revision.

| Column              | Type         | Constraints       | Description                          |
|---------------------|--------------|-------------------|--------------------------------------|
| `activity_id`       | VARCHAR(36)  | PK                | UUID v7 (time-ordered)               |
| `activity_type`     | VARCHAR(36)  | NOT NULL          | Create, Update, Delete               |
| `resource_type`     | VARCHAR(36)  |                   | ActivityResourceType enum value      |
| `resource_id`       | VARCHAR(255) |                   | Business ID of the affected entity   |
| `epoch_timestamp_ms`| BIGINT       |                   | Transaction timestamp (epoch millis) |
| `initiated_author`  | VARCHAR(255) |                   | Username who initiated the change    |
| `initiated_email`   | VARCHAR(320) |                   | Email of the initiator               |
| `revision`          | INTEGER      | FK → revinfo(id)  | Revision this activity belongs to    |

**Annotations**: `@Entity`, `@Table`. `activityId` uses `@JdbcTypeCode(SqlTypes.VARCHAR)` with UUID type. `activityType` and `resourceType` use `@Enumerated(EnumType.STRING)`.

---

## New Enums

### ActivityType

```
Create, Update, Delete
```

Maps from Envers `RevisionType`: ADD → Create, MOD → Update, DEL → Delete.

### ActivityResourceType

```
AdapterDeployment,
ApplicationDeployment,
InterceptorDeployment,
McpDeployment,
NimDeployment,
InferenceDeployment,
AdapterImageDefinition,
ApplicationImageDefinition,
InterceptorImageDefinition,
McpImageDefinition,
ImageBuildDomainWhitelist
```

Maps from JPA entity class to resource type. Each value corresponds to one of the 11 concrete audited entity classes. Base types `DeploymentEntity` and `ImageDefinitionEntity` are deliberately excluded — they are never persisted directly (only via concrete subtypes). If Envers fires `entityChanged` with a base class, the mapper throws `IllegalArgumentException` to surface the bug.

---

## Entity Modifications (Existing)

All 13 audited entities receive the `@Audited` annotation. For JOINED inheritance hierarchies, placing `@Audited` on the base class propagates to all subclasses.

### DeploymentEntity (base)
- Add `@Audited` at class level
- Envers creates `deployment_aud` with all base columns + `rev` + `revtype`

### Deployment Subtypes
Each subtype inherits `@Audited`. Envers creates `{table}_aud` with subtype-specific columns + `rev` + `revtype`:
- `adapter_deployment_aud` — no additional columns
- `application_deployment_aud` — no additional columns
- `interceptor_deployment_aud` — no additional columns
- `mcp_deployment_aud` — `transport`
- `nim_deployment_aud` — `container_grpc_port`
- `inference_deployment_aud` — `model_format`

### ImageDefinitionEntity (base)
- Add `@Audited` at class level
- Envers creates `image_definition_aud` with all base columns + `rev` + `revtype`

### Image Definition Subtypes
- `adapter_image_definition_aud` — no additional columns
- `application_image_definition_aud` — no additional columns
- `interceptor_image_definition_aud` — no additional columns
- `mcp_image_definition_aud` — `transport_type`

### DomainWhitelistEntity
- Add `@Audited` at class level
- Envers creates `domain_whitelist_aud` with all columns + `rev` + `revtype`

### Collection Tables
- `deployment_topics_aud` — if `deployment_topics` is mapped as `@ElementCollection` on an audited entity, Envers automatically audits it. Columns: `rev` + `revtype` + `deployment_id` + `topic_name`.

---

## Audit Table Structure Pattern

Each `*_aud` table follows this structure:

```
{original_table_name}_aud
├── rev       INTEGER  NOT NULL  (FK → revinfo.id)
├── revtype   SMALLINT           (0=ADD, 1=MOD, 2=DEL)
├── {pk_column(s)}               (same type as original PK)
└── {all other columns}          (same types, all nullable for DEL snapshots)
```

For JOINED inheritance base tables, `revtype` is on the base `_aud` table only. Subclass `_aud` tables contain `rev` + PK + subclass-specific columns (no `revtype`).

---

## Migration Summary

All migrations at version **V1.55** across three dialects (H2, POSTGRES, MS_SQL_SERVER):

| Table                             | Purpose                        |
|-----------------------------------|--------------------------------|
| `revinfo`                         | Envers revision metadata       |
| `deployment_aud`                  | Deployment base audit trail    |
| `adapter_deployment_aud`          | Adapter deployment audit       |
| `application_deployment_aud`      | Application deployment audit   |
| `interceptor_deployment_aud`      | Interceptor deployment audit   |
| `mcp_deployment_aud`              | MCP deployment audit           |
| `nim_deployment_aud`              | NIM deployment audit           |
| `inference_deployment_aud`        | Inference deployment audit     |
| `image_definition_aud`            | Image definition base audit    |
| `adapter_image_definition_aud`    | Adapter image def audit        |
| `application_image_definition_aud`| Application image def audit    |
| `interceptor_image_definition_aud`| Interceptor image def audit    |
| `mcp_image_definition_aud`        | MCP image def audit            |
| `domain_whitelist_aud`            | Domain whitelist audit         |
| `deployment_topics_aud`           | Deployment topics audit        |
| `audit_activity`                  | Denormalized activity feed     |

Total: 16 new tables per dialect.
