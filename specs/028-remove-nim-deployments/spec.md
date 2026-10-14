# Feature Specification: Remove NIM Deployment Support

**Feature Branch**: `remove-nim-deployments`
**Created**: 2026-10-13
**Status**: Implemented
**Capability**: nim-deployments, deployments, kubernetes-manifests, kubernetes-cleanup, export-import, auditing, deployment-metrics, image-definitions, api-conventions, database-and-migrations
**Input**: User description: "we don't use NIM models anymore, I want you to clean code from anything NIM related"

## Clarifications

### Session 2026-10-13

- Q: Existing environments may still hold NIM deployments (rows, topics, audit history, activity entries) and live `NIMService` resources. How should the upgrade treat them? → A: A new migration deletes all NIM data and drops the NIM tables. Live `NIMService` resources are not touched by the application; operators remove them before upgrading (documented in the upgrade plan).
- Q: What happens when an export archive produced by an earlier release, containing NIM deployments, is imported? → A: The NIM entries are silently ignored and everything else imports. (Initially "reject with 400"; revised once it turned out the application-wide JSON mapper ignores unknown properties, so rejection would need a dedicated legacy guard.)
- Q: Should the change be staged (deprecate first, drop tables later) or done at once? → A: Single release, full removal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - NIM is no longer part of the API (Priority: P1)

As an API client (the admin frontend or an automation script), I get a clear client error when I try to use the NIM deployment type, so stale integrations fail fast instead of producing half-working deployments.

**Why this priority**: The API contract is the primary surface of the removal; without it, NIM could still be created even though nothing can deploy it.

**Independent Test**: `POST /api/v1/deployments` with `"$type": "nim"` and `GET /api/v1/deployments?type=NIM` both return 400.

**Acceptance Scenarios**:

1. **Given** the upgraded service, **When** a client creates a deployment with `"$type": "nim"` (or an `ngc_registry` source), **Then** the service responds 400 with an `ErrorView` whose message names the unknown type id `nim`.
2. **Given** the upgraded service, **When** a client lists deployments filtered by `type=NIM`, **Then** the service responds 400 (not 500) with message `Invalid value 'NIM' for parameter 'type'`.
3. **Given** the upgraded service, **When** a client requests an export selecting component type `NIM_DEPLOYMENT`, **Then** the service responds 400.

---

### User Story 2 - Existing installations upgrade cleanly (Priority: P1)

As an operator of an installation that used NIM, I upgrade and the service starts on all supported databases, with every NIM record removed and all non-NIM data and history intact.

**Why this priority**: A failed or data-corrupting upgrade blocks every installation that ever used NIM.

**Independent Test**: Migrate a database at schema 1.59 seeded with NIM and non-NIM data (including a non-NIM deployment that reuses a former NIM id) to the latest version and verify exactly which rows remain.

**Acceptance Scenarios**:

1. **Given** a database with NIM deployments, their topics, pending component removals, audit revisions and activity-feed entries, **When** the service starts on the new version, **Then** all of those NIM rows are gone and the `nim_deployment` / `nim_deployment_aud` tables no longer exist.
2. **Given** a non-NIM deployment whose id was previously used by a deleted NIM deployment, **When** the migration runs, **Then** only the audit revisions that belonged to the NIM deployment are removed; the non-NIM deployment's revisions survive.
3. **Given** queued cleanup entries for a NIM deployment, **When** the migration runs, **Then** the `NIM_SERVICE` entry is removed (the type no longer exists) while the deployment's other queued resources (secrets, network policies) stay queued for the cleaner.
4. **Given** an operator still sets `K8S_NIM_*` / `NIM_SERVICE_*` environment variables, **When** the service starts, **Then** it starts normally and registers no NIM components.

---

### User Story 3 - Old export archives still import (Priority: P2)

As an administrator moving configuration from an older installation, I can import an export archive that contains NIM deployments; the NIM entries are ignored and everything else imports.

**Why this priority**: Keeps configuration portability across the release boundary; losing NIM entries is acceptable because NIM is retired.

**Independent Test**: Import (and preview) an archive containing a `nimDeployments` block alongside an MCP deployment.

**Acceptance Scenarios**:

1. **Given** an archive with a top-level `nimDeployments` map and an MCP deployment, **When** it is previewed and imported, **Then** both succeed, the MCP deployment is created, and no NIM data is persisted.

---

### Edge Cases

- A deployment id reused across a NIM and a non-NIM deployment (ids are user-chosen and reusable after deletion) — audit rows are matched on `(id, rev)`, never id alone.
- Rolling upgrade with several replicas — old replicas query the dropped `nim_deployment` table and error until the rollout completes; the upgrade plan requires a single replica / Recreate strategy.
- Live `NIMService` resources whose background deletion failed before the upgrade — the migration removes their cleanup-queue rows, so operators must verify `kubectl get nimservices -A` is empty before upgrading.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The API MUST NOT accept or return the NIM deployment type, the `ngc_registry` source type, the `NIM_DEPLOYMENT` export component type, or `nimDeployments` in import previews; such requests MUST yield 400.
- **FR-002**: Unconvertible path/query parameters (e.g. an unknown enum value) MUST yield 400 with message `Invalid value '<value>' for parameter '<name>'` instead of 500.
- **FR-003**: Schema migration `V1.60__RemoveNimDeployment` MUST, on H2, PostgreSQL and SQL Server, delete NIM activity-feed entries, NIM audit revisions (matched on `(id, rev)`), NIM deployments with their topics and pending component removals, and `NIM_SERVICE` cleanup-queue entries; then drop `nim_deployment` and `nim_deployment_aud`. `revinfo` rows MUST be kept.
- **FR-004**: The migration MUST NOT delete any non-NIM deployment, its subtype row, topics, audit revisions, activity entries, or queued non-`NIM_SERVICE` cleanup resources.
- **FR-005**: The service MUST contain no NIM Kubernetes integration (CRD, client, informer, manifest generation, deployment manager) and no NIM configuration properties; leftover NIM environment variables MUST be ignored.
- **FR-006**: Import and import preview of archives containing a `nimDeployments` block MUST succeed and ignore that block.
- **FR-007**: Already-applied migrations (V1.1–V1.59, SQL and Java) MUST remain unchanged.
- **FR-008**: Operator documentation MUST list the removed environment variables (0.23.0 changelog), the data deletion, the mandatory pre-upgrade database backup (`V1.60` is irreversible; rollback = restore backup + 0.22.x), the pre-upgrade `NIMService` cleanup, the post-upgrade cleanup of leftover NIM PVCs and NGC secrets (plus NGC key revocation), and the single-replica upgrade requirement (`docs/upgrade-plans/0.23.0.md`).

### Key Entities

- **NIM deployment** (removed): former deployment subtype backed by `nim_deployment` / `nim_deployment_aud`, NGC registry source, `NIMService` CRD.
- **Audit revision**: `deployment_aud` / `deployment_topics_aud` rows keyed by `(id, rev)`; NIM-owned revisions are identified via `nim_deployment_aud`.
- **Cleanup-queue entry**: `disposable_resource` row whose `resource_reference` JSON carries a `K8sResourceKind`; `NIM_SERVICE` is removed from that enum.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A case-insensitive search for NIM identifiers in production code returns nothing outside applied migrations.
- **SC-002**: The cross-vendor migration test passes on H2, PostgreSQL and SQL Server, proving FR-003/FR-004 including the reused-id case.
- **SC-003**: `./gradlew clean build` (checkstyle + full test suite including Testcontainers) passes.
- **SC-004**: The three API regression tests (create with `nim`, list with `type=NIM`, legacy archive import) pass.

## Assumptions

- No installation still needs NIM; permanent deletion of NIM data and history is accepted.
- The admin frontend ships a matching change removing the NIM type.
