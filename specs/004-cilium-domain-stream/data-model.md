# Data Model: Cilium Domain Access Streaming

**Feature**: 004-cilium-domain-stream  
**Phase**: 1  
**Date**: 2025-03-13

## Entities

### AccessedDomain (new)

Represents one distinct domain accessed during an image build and its Cilium verdict.

| Field   | Type              | Description |
|---------|-------------------|-------------|
| domain  | String            | Domain that was accessed (e.g. from DNS query; trailing dot stripped if present). |
| verdict | AccessVerdict     | ALLOWED if any access to this domain was allowed by Cilium, otherwise BLOCKED. |

**Validation**: domain must be non-blank; verdict is required.

**Persistence**: Stored as part of `ImageDefinitionEntity.accessedDomains` (element collection or JSON column per vendor). No separate table; same pattern as `build_logs` (JSON list).

---

### AccessVerdict (enum)

| Value   | Meaning |
|---------|--------|
| ALLOWED | At least one access to this domain was allowed by Cilium. |
| BLOCKED | All accesses to this domain were blocked (or at least one was DROPPED and none FORWARDED). |

---

### ImageDefinitionEntity (extended)

Existing entity; add:

| Field            | Type                         | Description |
|------------------|------------------------------|-------------|
| accessedDomains  | List&lt;AccessedDomain&gt;    | Distinct domains accessed during the build with verdict; one entry per domain; outcome = allowed if any access allowed, else blocked. |

**Persistence**: Column `accessed_domains` (JSON type per vendor: H2 `json`, PostgreSQL `jsonb`, SQL Server `nvarchar(max)` with JSON). Stored as array of `{ "domain": string, "verdict": "ALLOWED" | "BLOCKED" }`.

**Lifecycle**: Cleared/reset when a new build starts (same as build logs); appended/updated during build by Hubble integration; read by SSE stream and by GET details.

---

### ImageDefinition (domain model, extended)

Existing domain model; add:

| Field            | Type                         | Description |
|------------------|------------------------------|-------------|
| accessedDomains  | List&lt;AccessedDomain&gt;    | Same semantics as entity; used in service and API. |

---

## Propagation Across Layers

| Layer      | Artifact | Change |
|------------|----------|--------|
| **DB**     | Flyway V1.50 | Add `accessed_domains` column (JSON) to `image_definition` for H2, POSTGRES, MS_SQL_SERVER. |
| **Entity** | ImageDefinitionEntity | Add `accessedDomains` (e.g. `@JdbcTypeCode(SqlTypes.JSON)` or element collection with embeddable). |
| **DAO**    | PersistenceImageDefinitionMapper | Map `List<AccessedDomain>` to/from persistence (JSON or embeddable list). |
| **DAO**    | ImageDefinitionRepository | Add `addAccessedDomains(UUID id, List<AccessedDomain>)`, `resetAccessedDomains(UUID id)`; implement similar to addBuildLogs (load entity, merge list, save). |
| **Service**| ImageDefinitionService | Add `addAccessedDomain(id, domain, verdict)`, `addAccessedDomains(id, list)`, `resetAccessedDomains(id)`; call repository. |
| **Domain** | ImageDefinition, AccessedDomain | Add model type AccessedDomain (domain, verdict); add accessedDomains to ImageDefinition. |
| **Web DTO**| ImageBuildDetailsDto | Add `accessedDomains: List<AccessedDomainDto>` (or list of records with domain + verdict). |
| **Web**    | ImageBuildDetailsDtoMapper | Map `accessedDomains` from domain to DTO. |

---

## State and Uniqueness

- **Distinct domain**: Within a single build, only one entry per domain; verdict is “allowed if any access allowed, otherwise blocked.” Implementation can maintain a map (domain → verdict) in memory during the build and flush to entity as list; or merge on each add (read current list, update or add entry for domain, save).
- **Order**: Spec states no ordering guarantee; list order is implementation-defined.
- **Replay**: On SSE reconnect, the server sends previously persisted `accessedDomains` (and may then send new ones as they appear); no ordering guarantee.

---

## Migration (Flyway V1.50)

- **H2**: `ALTER TABLE image_definition ADD COLUMN IF NOT EXISTS accessed_domains JSON DEFAULT '[]'` (or equivalent).
- **POSTGRES**: `ALTER TABLE image_definition ADD COLUMN IF NOT EXISTS accessed_domains JSONB DEFAULT '[]'::jsonb`.
- **MS_SQL_SERVER**: `ALTER TABLE image_definition ADD accessed_domains NVARCHAR(MAX) DEFAULT '[]'` (JSON stored as string).

Default `[]` so existing rows have an empty list; new builds populate the list when Cilium is enabled and Hubble is used.
