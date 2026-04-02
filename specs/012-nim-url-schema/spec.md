# Feature Specification: NIM Service URL Schema Prefix

**Feature Branch**: `012-nim-url-schema`  
**Created**: 2026-03-31  
**Status**: Draft  
**Input**: User description: "NIM service contains URL without schema (http://, https://), but we need to return URL with schema. You need to modify URL extraction logic, so schema prefix would be added depending on what URL is used. If clusterEndpoint - add http, if externalEndpoint - add https. Ability to overwrite schema should be present."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Default Schema Prefix Applied to NIM Service URLs (Priority: P1)

When the system resolves a NIM service URL from the Kubernetes NIMService resource, the returned URL must include the appropriate schema prefix. Cluster-internal endpoints receive an `http://` prefix; external endpoints receive an `https://` prefix. This ensures downstream consumers (e.g., DIAL Core) receive fully-qualified URLs they can use directly without guessing the protocol.

**Why this priority**: Without a schema prefix, URLs are unusable by HTTP clients. This is the core behavior that makes NIM deployments functional for callers expecting a complete URL.

**Independent Test**: Can be tested by deploying a NIM service, reading its resolved URL, and verifying the schema prefix matches the endpoint type (http for cluster, https for external).

**Acceptance Scenarios**:

1. **Given** a NIM service with a populated `clusterEndpoint` in its status and `useClusterInternalUrl` is `true`, **When** the system resolves the service URL, **Then** the returned URL starts with `http://` followed by the original endpoint value.
2. **Given** a NIM service with a populated `externalEndpoint` in its status and `useClusterInternalUrl` is `false`, **When** the system resolves the service URL, **Then** the returned URL starts with `https://` followed by the original endpoint value.
3. **Given** a NIM service whose endpoint value already contains a schema prefix (e.g., `https://example.com`), **When** the system resolves the service URL, **Then** the existing schema is preserved and no duplicate prefix is added.

---

### User Story 2 - Operator Overrides Default Schema Prefix (Priority: P2)

An operator can override the default schema prefix via configuration. This allows environments where the convention (http for cluster, https for external) does not hold — for example, a cluster using mTLS internally that requires `https://` for cluster endpoints, or a test environment using `http://` for external endpoints.

**Why this priority**: Some environments have non-standard networking setups. Without an override, those environments would receive incorrect URLs, breaking connectivity.

**Independent Test**: Can be tested by setting the schema override configuration property and verifying the resolved URL uses the overridden schema instead of the default.

**Acceptance Scenarios**:

1. **Given** a schema override is configured (e.g., override set to `https`), **When** the system resolves a cluster-internal URL, **Then** the returned URL uses the overridden schema (`https://`) instead of the default (`http://`).
2. **Given** a schema override is configured (e.g., override set to `http`), **When** the system resolves an external URL, **Then** the returned URL uses the overridden schema (`http://`) instead of the default (`https://`).
3. **Given** no schema override is configured (property is empty or absent), **When** the system resolves a URL, **Then** the default schema logic applies (http for cluster, https for external).

---

### Edge Cases

- What happens when the endpoint value is `null` or empty? The system should return `null` (no URL available), same as current behavior.
- What happens when the endpoint already includes a schema prefix? The system must detect the existing prefix and avoid prepending a second one.
- What happens when the override value includes the `://` separator (e.g., `https://`)? The system should handle both `https` and `https://` gracefully and produce a correctly formatted URL.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST prepend `http://` to cluster-internal endpoint URLs when resolving NIM service URLs and no schema override is configured.
- **FR-002**: System MUST prepend `https://` to external endpoint URLs when resolving NIM service URLs and no schema override is configured.
- **FR-003**: System MUST NOT prepend a schema prefix if the endpoint URL already contains a schema (e.g., starts with `http://` or `https://`).
- **FR-004**: System MUST support a configuration property that allows operators to override the default schema prefix for resolved NIM service URLs.
- **FR-005**: When a schema override is configured, the system MUST use the overridden schema for all resolved NIM service URLs regardless of endpoint type.
- **FR-006**: System MUST handle override values with or without the `://` suffix (e.g., both `https` and `https://` should produce `https://`).
- **FR-007**: System MUST continue to return `null` when the endpoint value is `null` or empty, regardless of schema configuration.

### Key Entities

- **NimDeployProperties**: Configuration holder for NIM deployment settings; extended with a schema override property.
- **NIMService Status Model**: Kubernetes CRD status containing `clusterEndpoint` and `externalEndpoint` fields whose values may lack a schema prefix.

## Assumptions

- The current `clusterEndpoint` and `externalEndpoint` values returned by the NIM Kubernetes operator consistently lack a schema prefix. If the operator changes behavior and starts returning prefixed URLs, FR-003 ensures no double-prefix occurs.
- A single schema override property applies globally to all NIM service URL resolutions. Per-deployment overrides are not needed at this time.
- The override property affects only the schema portion of the URL; it does not alter the host, port, or path.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All NIM service URLs returned by the system include a valid schema prefix (`http://` or `https://`), verified by automated tests covering both endpoint types.
- **SC-002**: Operators can override the default schema via a single configuration property, with the override taking effect without code changes or redeployment beyond configuration update.
- **SC-003**: Existing NIM deployments continue to function correctly after the change — no regressions in URL resolution for either cluster-internal or external endpoint modes.
- **SC-004**: URLs that already contain a schema prefix are returned unchanged, preventing malformed double-prefixed URLs.
