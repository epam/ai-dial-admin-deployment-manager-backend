# Auditing

## Purpose
This spec describes the change-tracking and activity-feed capability — Hibernate Envers preserves the full historical state of audited entities, and a denormalized activity feed exposes who-changed-what queries to administrators. Implemented via 014-auditing.

Status: **Implemented**

## Key Terms
- **Activity**: A single tracked change event (Create / Update / Delete) on an audited resource — captures activity type, resource type, resource identifier, author name, author email, and timestamp. Linked to a revision.
- **Revision**: A grouping of all changes within a single transaction — sequential id, transaction timestamp, initiating author identity. One revision → many activities.
- **Audit Snapshot**: A historical copy of an entity's complete state at the time of a change, persisted to Envers `_AUD` tables and retrieved via `AuditReader`.
- **Activity feed**: The denormalized, query-optimized table that powers `/api/v1/activities`; complements raw Envers history.

## Audited resource types
`AdapterDeployment`, `ApplicationDeployment`, `InterceptorDeployment`, `McpDeployment`, `NimDeployment`, `InferenceDeployment`, `AdapterImageDefinition`, `ApplicationImageDefinition`, `InterceptorImageDefinition`, `McpImageDefinition`, and `DomainWhitelist`.

`DisposableResource` and `ComponentRemoval` are excluded as internal housekeeping. Base types (`Deployment`, `ImageDefinition`) are audited at the database level via Envers but are not exposed as activity resource types — entities are persisted as concrete subtypes only.

## Requirements

### Requirement: All CUD operations on audited entities produce an activity record
The system SHALL track every insert, update, and delete on audited entities, preserving full entity state at each change point and recording a corresponding activity entry.

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Create activity recorded
- **WHEN** an administrator creates a new deployment
- **THEN** a `Create` activity for that deployment appears in the activity feed with the correct author, timestamp, and resource identifier

#### Scenario: Update activity recorded
- **WHEN** an administrator updates a deployment's configuration
- **THEN** an `Update` activity reflecting the change appears in the activity feed

#### Scenario: Delete activity recorded
- **WHEN** an administrator deletes a deployment
- **THEN** a `Delete` activity for that deployment appears in the activity feed

#### Scenario: No activity on rollback
- **WHEN** a transaction modifying audited entities rolls back
- **THEN** no audit records are persisted (audit records are part of the same transaction)

### Requirement: Author identity captured from security context
The system SHALL extract the username and email of the user who initiated each change from the authenticated security context. Unauthenticated HTTP requests record `"unknown"`; non-HTTP contexts (informer callbacks, scheduled tasks, build pipeline) record `"system"`.

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Authenticated user attribution
- **WHEN** an authenticated administrator modifies an audited resource
- **THEN** the activity records the administrator's username and email

#### Scenario: Internal API attribution
- **WHEN** an internal HTTP request without auth modifies an audited resource
- **THEN** the activity records `"unknown"` as author

#### Scenario: Background task attribution
- **WHEN** a K8s informer callback or scheduled task modifies an audited resource
- **THEN** the activity records `"system"` as author

### Requirement: Transaction-consistent timestamp and revision
The system SHALL assign a single timestamp and revision id to all audit records produced within one transaction so they can be correlated.

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Batch update shares revision
- **WHEN** multiple audited entities are modified within a single transaction
- **THEN** all resulting activity entries and audit snapshots share the same revision id and timestamp

### Requirement: Activity deduplication within a transaction
When the same resource is affected multiple times within a single transaction, the system SHALL retain a single activity entry for that resource — `Create` or `Delete` takes precedence over `Update`.

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Multiple updates collapse
- **WHEN** the same resource is updated twice within a single transaction
- **THEN** a single `Update` activity entry exists for that resource

#### Scenario: Create + update collapse to create
- **WHEN** a resource is created and then updated within a single transaction
- **THEN** only the `Create` activity is retained for that resource

### Requirement: Activity list and detail endpoints
The system SHALL expose `POST /api/v1/activities` for filtered listing with pagination and `GET /api/v1/activities/{activityId}` for single-record retrieval. Filters are `{column, operator, value}` tuples supporting `eq`, `ne`, `lt`, `le`, `gt`, `ge`, `co`, `nc`. Filterable columns: activity id, activity type, resource type, resource identifier, author name, author email, transaction timestamp.

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Filter by resource type
- **WHEN** an administrator filters by resource type `McpDeployment`
- **THEN** only activities for `McpDeployment` are returned

#### Scenario: Filter by activity type
- **WHEN** an administrator filters by activity type `Delete`
- **THEN** only delete activities are returned

#### Scenario: Activity not found
- **WHEN** the administrator requests an activity by an unknown identifier
- **THEN** the system returns HTTP 404 with an `ErrorView` body

### Requirement: Revision list and lookup endpoints
The system SHALL expose `POST /api/v1/history/revisions` for filtered listing with pagination and `POST /api/v1/history/revisions/query` for single-revision lookup by id or by timestamp (most recent at or before).

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Revision query by timestamp
- **WHEN** the administrator queries by timestamp `T2` (where revisions exist at `T1 < T2 < T3`)
- **THEN** the revision at `T1` is returned (most recent at or before)

#### Scenario: Revision query no match
- **WHEN** no revision exists at or before the given timestamp
- **THEN** the system returns HTTP 404

### Requirement: Per-controller entity snapshot endpoints
Each audited entity controller SHALL expose `GET /{id}/revision/{revision}` returning the entity DTO at the specified revision, and `GET /revision/{revision}` returning all entities of that type at the specified revision. Snapshot DTOs are the same shape as the current-state DTOs (no separate `EntityRevisionDto` wrapper — rollback is out of scope).

Status: **Implemented** (Implemented via 014-auditing)

#### Scenario: Snapshot at past revision
- **WHEN** a deployment exists at revisions `R1` and `R2` and the administrator requests its snapshot at `R1`
- **THEN** the original state is returned

#### Scenario: Snapshot before creation
- **WHEN** an entity was created at revision `R1` and the administrator requests its snapshot at a revision before `R1`
- **THEN** the system returns HTTP 404

#### Scenario: Deleted entity in collection snapshot
- **WHEN** an image definition was deleted at revision `R2` and the administrator requests all image definitions at revision `R1`
- **THEN** the deleted image definition is included in the result

### Requirement: Multi-vendor migration support
Audit schema changes SHALL be managed through versioned Flyway migrations compatible with H2, PostgreSQL, and SQL Server.

Status: **Implemented** (Implemented via 014-auditing)

## Implementation Notes
- ORM-level audit: Hibernate Envers via `@Audited` on entities; `_AUD` tables managed by Flyway migrations under `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`.
- Author capture: `dao/audit/listener/` revision listener writes username/email/timestamp into the revision row, sourced from the Spring Security context for HTTP requests and falling back to `"unknown"` / `"system"` per the rules above.
- Activity feed: denormalized `Activity` table populated transactionally; dedup logic (`Create`/`Delete` over `Update`) is applied per revision.
- Snapshot retrieval: Hibernate Envers `AuditReader` API; respects JOINED inheritance so querying `DeploymentEntity` at a revision returns the concrete subtype.
- Service layer: `service/audit/AuditActivityService.java` (activity feed queries), `service/audit/HistoryService.java` (revision and entity-snapshot queries).
- Controllers: `web/controller/AuditActivityController.java` (`/api/v1/activities`), `web/controller/HistoryController.java` (`/api/v1/history`); per-resource snapshot endpoints live on each entity's existing controller.
- Authorization: activity / history endpoints are accessible to all authenticated users (no `@FullAdminOnly` restriction).
- Related specs: `deployments`, `mcp-deployments`, `interceptor-deployments`, `adapter-deployments`, `application-deployments`, `inference-deployments`, `nim-deployments`, `image-definitions`, `mcp-image-definitions`, `interceptor-image-definitions`, `adapter-image-definitions`, `application-image-definitions`, `domain-whitelist`, `security`, `api-conventions`.
