# Domain Whitelist

## Purpose
This spec describes the global domain whitelist for image builds — a configurable list of domains that the build process is permitted to access during container image construction.

Status: **Implemented**

## Key Terms
- **Domain whitelist**: The global list of allowed external domain names that build pipelines may access. Prevents unauthorized network egress during build.
- **Image-build whitelist**: The specific whitelist scoped to image build operations (currently the only whitelist type).

## Requirements

### Requirement: Get global domain whitelist for image builds
The system SHALL return the current global domain whitelist applied during image builds.

Status: **Implemented**

#### Scenario: Retrieve whitelist
- **WHEN** `GET /api/v1/global-whitelist/image-build` is called
- **THEN** the current list of whitelisted domain entries is returned with HTTP 200

### Requirement: Replace global domain whitelist for image builds
The system SHALL allow replacing the global domain whitelist with a new set of domains. This is a full replacement (not additive).

Status: **Implemented**

#### Scenario: Successful replace
- **WHEN** `POST /api/v1/global-whitelist/image-build` is called with a valid domain list
- **THEN** the global domain whitelist is replaced with the provided list

#### Scenario: Invalid domain format rejected
- **WHEN** `POST /api/v1/global-whitelist/image-build` is called with an invalid domain entry
- **THEN** the system responds with 400

### Requirement: Domain whitelist enforced during image builds
The global domain whitelist SHALL be applied by the build pipeline to restrict network access, preventing containers from reaching unauthorized external domains during image construction.

Status: **Implemented**

#### Scenario: Whitelist applied at build time
- **WHEN** an image build is triggered
- **THEN** the current global domain whitelist is read and passed to the build pipeline as access restrictions

## Domain Access Control Layers

The project has three distinct domain-restriction mechanisms at different scopes:

| Layer | Scope | Purpose | Spec |
|---|---|---|---|
| **Global domain whitelist** (this spec) | All image builds | Restricts which external domains any build pipeline may access | `domain-whitelist` |
| **Per-image-definition `allowedDomains`** | Single image definition | Additional domains allowed during that specific image's build | `image-definitions` |
| **Per-deployment `allowedDomains`** | Single deployment | Domains the running deployment may access at runtime (enforced via Cilium network policy) | `deployments` |

The first two apply at **build time**, the third at **runtime**. They are independent — the global whitelist does not affect deployment runtime network policies, and per-deployment allowed domains do not affect builds.

### Requirement: Import merges rather than replaces
When the global domain whitelist is imported via `POST /api/v1/configs/import` with `OVERWRITE` policy, existing whitelist entries are preserved and incoming entries are appended (deduplicated). This differs from the direct `POST /api/v1/global-whitelist/image-build` endpoint which performs a full replacement.

Status: **Implemented**

### Requirement: Revision rollback
The system SHALL expose `POST /api/v1/global-whitelist/image-build/revision/{revision}/rollback` to restore the global image-build whitelist to its snapshot at the supplied audit revision. Rollback is a full replacement (not a merge), matching the direct-replace semantics of the regular write endpoint. The supplied revision must exist (validated against `historyService.getRevisionById`) and must contain a whitelist snapshot; missing revisions reject with HTTP 404. If the whitelist singleton row does not currently exist (fresh DB, removed singleton, etc.) the rollback recreates it from the snapshot rather than rejecting with 404 — the snapshot itself is the source of truth for the restored state. Identical-state rollbacks are no-ops that do not produce a new revision.

Status: **Implemented** (Implemented via 020-revision-rollback)

#### Scenario: Rollback to a past revision
- **WHEN** `POST /api/v1/global-whitelist/image-build/revision/{revision}/rollback` is called with a valid revision
- **THEN** the current whitelist entries are replaced by a full copy of the snapshot at that revision, a new audit revision is recorded, and HTTP 200 is returned with the resulting `List<String>` of domains

#### Scenario: Rollback no-op when state matches
- **WHEN** the current whitelist already equals the snapshot at the supplied revision (multiset comparison)
- **THEN** the system returns HTTP 200 with the current entries and does NOT record a new revision

#### Scenario: Rollback to unknown revision
- **WHEN** the supplied revision does not exist
- **THEN** the system responds with HTTP 404 and the whitelist is unchanged

#### Scenario: Import merge
- **WHEN** `POST /api/v1/configs/import` is called with a ZIP containing a domain whitelist and `conflictResolutionPolicy=OVERWRITE`
- **THEN** the resulting whitelist is the union of the existing and incoming lists (existing entries first, new incoming entries appended, duplicates removed)

#### Scenario: Import with no existing whitelist
- **WHEN** `POST /api/v1/configs/import` is called and no global whitelist exists yet
- **THEN** the incoming whitelist is created as-is

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.GlobalDomainWhitelistController`
- Entity: `com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity`
- API path: `/api/v1/global-whitelist/image-build`
- Related specs: `image-builds`, `buildkit`, `image-definitions` (per-definition allowedDomains), `deployments` (per-deployment allowedDomains)
