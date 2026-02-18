# Domain Whitelist

## Purpose
This spec describes the global domain whitelist for image builds — a configurable list of domains that the build process is permitted to access during container image construction.

Status: **Implemented**

## Key Terms
- **Domain whitelist**: The global list of allowed external domain names that build pipelines may access. Prevents unauthorized network egress during build.
- **Image-build whitelist**: The specific whitelist scoped to image build operations (currently the only whitelist type).

## ADDED Requirements

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

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.GlobalDomainWhitelistController`
- Entity: `com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity`
- API path: `/api/v1/global-whitelist/image-build`
- Related specs: `image-builds`, `buildkit`, `image-definitions` (per-definition allowedDomains), `deployments` (per-deployment allowedDomains)
