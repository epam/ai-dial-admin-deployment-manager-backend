# Feature Specification: Auditing

**Feature Branch**: `014-auditing`
**Created**: 2026-04-14
**Status**: Implemented
**Capability**: N/A — creates new capability auditing
**Input**: User description: "Add auditing in ai-dial-admin-deployment-manager-backend based on ai-dial-admin-backend patterns. Use the same patterns, technologies and endpoints."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Activity History for Deployment Resources (Priority: P1)

An administrator wants to see a chronological list of all changes made to deployment resources (who changed what, when, and how) so they can audit configuration changes, troubleshoot issues, and maintain compliance.

**Why this priority**: Activity history is the primary consumer-facing capability of the auditing system. Without a queryable activity feed, the underlying change tracking has no user value.

**Independent Test**: Can be fully tested by creating, updating, and deleting deployments, then querying the activity endpoint to verify the changes appear with correct details (author, timestamp, action type, resource).

**Acceptance Scenarios**:

1. **Given** an administrator has created a new deployment, **When** they query the activity list endpoint, **Then** they see a "Create" activity entry for that deployment with the correct author, timestamp, and resource identifier.
2. **Given** an administrator has updated a deployment's configuration, **When** they query the activity list endpoint, **Then** they see an "Update" activity entry reflecting that change.
3. **Given** an administrator has deleted a deployment, **When** they query the activity list endpoint, **Then** they see a "Delete" activity entry for that deployment.
4. **Given** multiple changes occurred in a single operation (e.g., batch update), **When** they query the activity list, **Then** all changes from that operation share the same timestamp and revision.

---

### User Story 2 - Filter and Search Activities (Priority: P1)

An administrator wants to filter the activity list by various criteria (resource type, activity type, author, timestamp range) so they can quickly find specific changes without scrolling through the entire history.

**Why this priority**: Without filtering, the activity feed becomes unusable at scale. This is essential for practical audit investigations.

**Independent Test**: Can be tested by creating activities across different resource types and authors, then verifying that filter combinations return the correct subset.

**Acceptance Scenarios**:

1. **Given** the activity list contains entries for multiple resource types, **When** the administrator filters by a specific resource type (e.g., "McpDeployment"), **Then** only activities for that resource type are returned.
2. **Given** the activity list contains entries from multiple authors, **When** the administrator filters by author, **Then** only activities initiated by that author are returned.
3. **Given** the activity list contains create, update, and delete activities, **When** the administrator filters by activity type "Delete", **Then** only delete activities are returned.
4. **Given** a large activity list, **When** the administrator requests paginated results, **Then** the response includes correct pagination metadata and the requested page of results.

---

### User Story 3 - Retrieve a Single Activity Detail (Priority: P2)

An administrator wants to view the full details of a specific activity entry by its identifier so they can investigate a particular change in depth.

**Why this priority**: Complements the list view by allowing drill-down into individual activity records. Lower priority because the list endpoint already returns full activity details.

**Independent Test**: Can be tested by creating a deployment change, capturing the resulting activity ID, and retrieving it via the detail endpoint to verify all fields are present and accurate.

**Acceptance Scenarios**:

1. **Given** an activity exists with a known identifier, **When** the administrator retrieves it by ID, **Then** the full activity record is returned including activity type, resource type, resource ID, author, email, and timestamp.
2. **Given** no activity exists with the provided identifier, **When** the administrator requests it, **Then** the system returns an appropriate "not found" response.

---

### User Story 4 - Track Changes to All Audited Resources (Priority: P1)

The system tracks changes to all managed resources — not just deployments but also image definitions, domain whitelist entries, and other configuration entities — so the activity feed provides a complete audit trail across the entire system.

**Why this priority**: Partial coverage undermines the value of auditing. Administrators need confidence that all changes are captured.

**Independent Test**: Can be tested by making changes to each resource type (image definitions, domain whitelist, deployments of all types) and verifying corresponding activity entries appear.

**Acceptance Scenarios**:

1. **Given** an administrator creates a new image definition, **When** they query the activity list filtered by resource type "ImageDefinition", **Then** a "Create" activity appears.
2. **Given** an administrator updates a domain whitelist entry, **When** they query the activity list, **Then** an "Update" activity for "ImageBuildDomainWhitelist" appears with the correct author.
3. **Given** changes are made to different resource types in separate operations, **When** the administrator queries without filters, **Then** all activities appear in reverse-chronological order.

---

### User Story 5 - Historical Entity Snapshots Preserved (Priority: P2)

The system preserves the full state of every audited entity at each point of change so that administrators or compliance tools can reconstruct what an entity looked like at any past revision.

**Why this priority**: While the activity feed covers "who did what," full historical snapshots are needed for compliance audits and rollback investigations. This is the underlying data layer that supports the activity feed.

**Independent Test**: Can be tested by modifying a deployment multiple times and verifying that each historical state is preserved and retrievable at the database level.

**Acceptance Scenarios**:

1. **Given** a deployment has been modified three times, **When** the historical records are queried, **Then** three distinct snapshots exist — each reflecting the entity state at the time of that change.
2. **Given** an entity has been deleted, **When** the historical records are queried, **Then** a deletion record exists alongside all prior state snapshots.

---

### User Story 6 - List Revision History (Priority: P2)

An administrator wants to see a paginated, filterable list of all revisions (transaction snapshots) to understand the timeline of system changes, identify who made changes, and correlate groups of related modifications.

**Why this priority**: Revision listing provides a higher-level view of system change history, complementing the activity feed. Useful for compliance and investigation but not required for day-to-day audit queries.

**Independent Test**: Can be tested by performing several CRUD operations to generate revisions, then querying the revision list endpoint to verify results include correct metadata (id, timestamp, author, email).

**Acceptance Scenarios**:

1. **Given** multiple CRUD operations have been performed, **When** the administrator queries the revision list endpoint with no filters, **Then** all revisions are returned with correct pagination metadata.
2. **Given** revisions exist from multiple authors, **When** the administrator filters by author, **Then** only revisions by that author are returned.
3. **Given** revisions span a time range, **When** the administrator filters by timestamp range, **Then** only revisions within that range are returned.
4. **Given** revisions exist, **When** the administrator sorts by timestamp DESC, **Then** the most recent revisions appear first.

---

### User Story 7 - Query Specific Revision (Priority: P2)

An administrator wants to look up a specific revision by its ID or find the most recent revision at a given point in time, for audit investigation or compliance reporting.

**Why this priority**: Enables point-in-time lookups, which are essential for correlating revisions with external events (e.g., incident timestamps). Lower priority than activity listing but required for full audit capability.

**Independent Test**: Can be tested by creating revisions, then querying by known ID and by timestamp to verify correct results and 404 behavior.

**Acceptance Scenarios**:

1. **Given** a revision exists with a known ID, **When** the administrator queries by ID, **Then** the full revision record is returned.
2. **Given** no revision exists with the provided ID, **When** the administrator queries by ID, **Then** the system returns a 404 response.
3. **Given** revisions exist at timestamps T1 and T3, **When** the administrator queries by timestamp T2 (where T1 < T2 < T3), **Then** the revision at T1 is returned (most recent at or before T2).
4. **Given** no revisions exist before the provided timestamp, **When** the administrator queries by timestamp, **Then** the system returns a 404 response.

---

### User Story 8 - View Entity Snapshot at Revision (Priority: P2)

An administrator wants to view the state of a specific entity (deployment, image definition, domain whitelist) as it was at a particular revision, for audit investigation, compliance reporting, or change comparison.

**Why this priority**: Entity snapshots expose the historical data captured by Envers, enabling administrators to see what an entity looked like at any past point. Supports compliance use cases and troubleshooting configuration regressions.

**Independent Test**: Can be tested by creating a deployment, modifying it, then querying the snapshot at the first revision to verify the original state is returned.

**Acceptance Scenarios**:

1. **Given** a deployment was created at revision R1 and updated at revision R2, **When** the administrator requests the deployment snapshot at R1, **Then** the original state is returned.
2. **Given** a deployment was created at revision R1, **When** the administrator requests the deployment snapshot at a revision before R1, **Then** the system returns a 404 response.
3. **Given** multiple deployments existed at revision R, **When** the administrator requests all deployments at revision R, **Then** all deployments that existed at that point are returned.
4. **Given** an image definition was deleted at revision R2, **When** the administrator requests all image definitions at revision R1 (before deletion), **Then** the deleted image definition is included in the results.
5. **Given** the domain whitelist had specific values at revision R, **When** the administrator requests the whitelist snapshot at R, **Then** the historical whitelist values are returned.

---

### Edge Cases

- What happens when an unauthenticated request modifies data (e.g., internal API calls)? The system records `"unknown"` as the author when an HTTP request is present but authentication is missing or invalid, and `"system"` when no security context exists at all (e.g., K8s informer callbacks, scheduled tasks, build pipeline operations).
- What happens when multiple entities are modified within a single transaction? All resulting activity entries share the same revision number and timestamp.
- What happens when the same entity is modified multiple times within a single transaction? The system deduplicates activities, keeping the most specific activity type (Create or Delete takes precedence over Update).
- What happens when an entity modification fails and the transaction rolls back? No audit records are created because they are part of the same transaction.
- What happens when the activity table grows very large? Pagination ensures the API remains responsive regardless of total activity count.
- What happens when querying a snapshot at a revision where the entity did not yet exist? The system returns a 404 response.
- What happens when querying all entities at a revision before any were created? The system returns an empty list.
- What happens when querying a revision by timestamp before any revisions exist? The system returns a 404 response.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST track all insert, update, and delete operations on audited entities and preserve the full entity state at each change point.
- **FR-002**: System MUST capture the identity (username and email) of the user who initiated each change, extracted from the authenticated security context.
- **FR-003**: System MUST assign a consistent timestamp to all audit records created within a single transaction so they can be correlated.
- **FR-004**: System MUST maintain a denormalized activity feed recording: a unique activity identifier, activity type (Create/Update/Delete), resource type, resource identifier, timestamp, author name, and author email for each tracked change.
- **FR-005**: System MUST expose a list endpoint that returns activities with support for pagination and sorting.
- **FR-006**: System MUST expose a detail endpoint that returns a single activity by its unique identifier.
- **FR-007**: System MUST support filtering activities by: activity ID, activity type, resource type, resource identifier, author name, author email, and transaction timestamp. Filters are expressed as `{column, operator, value}` tuples and support operators `eq`, `ne`, `lt`, `le`, `gt`, `ge`, `co`, `nc` (see `contracts/activities-api.md`). Timestamp ranges are expressed as two filters using `ge`/`gt` and `le`/`lt`.
- **FR-008**: System MUST deduplicate activities within a single transaction — if the same resource is affected multiple times, only the most specific activity type is retained (Create or Delete over Update).
- **FR-009**: System MUST audit the following resource types: AdapterDeployment, ApplicationDeployment, InterceptorDeployment, McpDeployment, NimDeployment, InferenceDeployment, AdapterImageDefinition, ApplicationImageDefinition, InterceptorImageDefinition, McpImageDefinition, and DomainWhitelist. DisposableResource and ComponentRemoval are excluded as internal housekeeping entities. Base types (Deployment, ImageDefinition) are audited at the database level via Envers but are not exposed as activity resource types — entities are always persisted as concrete subtypes.
- **FR-010**: System MUST manage audit schema changes through versioned database migrations compatible with all supported database dialects (H2, PostgreSQL, MS SQL Server).
- **FR-011**: System MUST NOT create audit records when a transaction rolls back.
- **FR-012**: System MUST expose a revision list endpoint (`POST /api/v1/history/revisions`) that returns revisions with pagination, sorting, and filtering on id, timestamp, author, and email (see `contracts/revisions-api.md`).
- **FR-013**: System MUST expose a revision query endpoint (`POST /api/v1/history/revisions/query`) that accepts a polymorphic request to find a revision by ID or by timestamp (most recent at or before the given timestamp).
- **FR-014**: Each audited entity controller MUST expose a snapshot endpoint (`GET /{id}/revision/{revision}`) that returns the entity DTO as it existed at the specified revision.
- **FR-015**: Each audited entity controller MUST expose a collection snapshot endpoint (`GET /revision/{revision}`) that returns all entities of that type as they existed at the specified revision.

### Key Entities

- **Activity**: Represents a single tracked change event — captures the activity type (Create/Update/Delete), the type and identifier of the affected resource, the author who initiated the change, and the timestamp. Each activity is linked to a revision.
- **Revision**: Groups all changes that occurred within a single transaction — includes a sequential identifier, the transaction timestamp, and the initiating user's identity. One revision may be linked to many activities.
- **Audit Snapshot**: A historical copy of an entity's complete state at the time of a change, linked to the revision that triggered it. One snapshot exists per entity per change event.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of create, update, and delete operations on audited entities produce corresponding activity records in the activity feed.
- **SC-002**: Every activity record correctly identifies the author who initiated the change (zero misattributed entries).
- **SC-003**: All changes within a single operation share the same timestamp and revision (zero timestamp drift within a transaction).
- **SC-004**: Administrators can retrieve a filtered page of activities within 2 seconds under normal operating conditions.
- **SC-005**: The activity list endpoint supports at least 5 simultaneous filter criteria without degradation.
- **SC-006**: Activity deduplication correctly collapses multiple modifications to the same resource within one transaction into a single activity entry.
- **SC-007**: Revision list endpoint returns correct paginated results with filtering within 2 seconds under normal operating conditions.
- **SC-008**: Revision query by timestamp returns the most recent revision at or before the given timestamp, or 404 if none exists.
- **SC-009**: Entity snapshot endpoints return the correct historical state matching the Envers audit trail for all audited entity types.

## Assumptions

- The existing authentication infrastructure (Spring Security with SecurityClaimsExtractor) will be reused to capture the author identity — no new authentication mechanism is needed.
- The auditing system follows the same two-tier architecture proven in ai-dial-admin-backend: automatic entity-level change snapshots plus a curated, denormalized activity feed.
- A transaction-scoped timestamp mechanism will be introduced to ensure consistent timestamps across all audit records within a single transaction (matching the AOP pattern from the reference implementation).
- The activity API endpoints follow the same REST conventions as the reference implementation: `POST /api/v1/activities` for filtered listing with pagination and `GET /api/v1/activities/{activityId}` for single-record retrieval.
- Database migrations will be created for all three supported dialects (H2, PostgreSQL, MS SQL Server) following existing Flyway conventions.
- DisposableResource and ComponentRemoval are excluded from auditing entirely as they represent internal housekeeping, not user-initiated configuration changes.
- Activity endpoints are accessible to all authenticated users (no `@FullAdminOnly` restriction), matching the reference implementation pattern.
- No UI changes are in scope — this feature provides the backend API only.
- Existing entities that already have `@CreatedDate`/`@LastModifiedDate` fields will retain those alongside the new audit tracking — the two mechanisms serve different purposes (current state timestamps vs. full change history).
- Revision listing follows the same REST conventions as the reference implementation: `POST /api/v1/history/revisions` for filtered listing with pagination and `POST /api/v1/history/revisions/query` for single-revision lookup.
- Entity snapshot endpoints use Hibernate Envers `AuditReader` API to retrieve historical entity state. Envers correctly preserves JOINED inheritance hierarchies (e.g., querying `DeploymentEntity` at a revision returns the concrete subtype).
- Snapshot endpoints return the same DTOs as the corresponding current-state endpoints (e.g., `DeploymentDto`, `ImageDefinitionDto`) — no `EntityRevisionDto` wrapper is needed since rollback is out of scope.
