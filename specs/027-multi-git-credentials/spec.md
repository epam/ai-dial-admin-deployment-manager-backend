# Feature Specification: Multiple Git Credentials per Domain

**Feature Branch**: `027-multi-git-credentials`
**Created**: 2026-09-08
**Status**: Implemented
**Capability**: N/A — creates new capability git-credentials
**Input**: User description: "I want to allow to set multiple git creds per one domain. A repo may have it's own creds, or we can have token with access to a project with multiple repos and all at the same time"

## Clarifications

### Session 2026-09-08

- Q: Today's single-credential-per-host config matches a host or any of its subdomains (e.g. a credential for `example.com` also applies to `git.example.com`). Should that subdomain fallback still apply once credentials can be scoped by path too? → A: Yes, preserve subdomain matching, consistent with today's behavior. In addition, when both an exact-host match and a subdomain (parent-domain) match are available, the exact-host match always takes precedence, regardless of path specificity — path specificity (repository > project/group > domain-wide) then breaks ties within the same host-match level.
- Q: Should a project/group scope's path prefix match only at path-segment boundaries, or as a plain string prefix? → A: Segment-boundary matching — a project scope of `team` matches `team/service-a` but does not match an unrelated repository like `team2/service-a`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Repository-specific credentials on a shared domain (Priority: P1)

As a platform operator, I configure a dedicated set of git credentials for one specific repository on a host, so that repository authenticates with its own credentials while other repositories on the same host keep using whatever credentials apply to them.

**Why this priority**: This is the core gap in today's behavior — only one credential set can be configured per host, so a repository needing its own access cannot coexist with other credentials on the same domain. Without this, the feature delivers no value.

**Independent Test**: Configure credentials for host `git.example.com` with a repository-specific entry for `git.example.com/team/service-a`. Trigger an image build sourced from that exact repository and confirm the build authenticates using the repository-specific credentials.

**Acceptance Scenarios**:

1. **Given** a repository-specific credential is configured for `git.example.com/team/service-a`, **When** an image build clones that exact repository, **Then** the build uses the repository-specific credential.
2. **Given** a repository-specific credential is configured for `git.example.com/team/service-a` and a separate domain-wide credential is configured for `git.example.com`, **When** an image build clones a different repository `git.example.com/team/service-b`, **Then** the build uses the domain-wide credential, not the repository-specific one.

---

### User Story 2 - One token covering every repository in a project (Priority: P2)

As a platform operator, I configure a single token scoped to a project or group (a path prefix under a host that covers multiple repositories), so every repository under that project authenticates automatically without configuring each repository individually.

**Why this priority**: Reduces configuration effort for teams whose access tokens already grant access to many repositories under one project/group, which is the common case for organization- or group-scoped tokens.

**Independent Test**: Configure a project-scoped credential for `git.example.com/team`. Trigger builds sourced from `git.example.com/team/service-a` and `git.example.com/team/service-c` (neither has its own repository-specific entry) and confirm both authenticate using the project-scoped credential.

**Acceptance Scenarios**:

1. **Given** a project-scoped credential is configured for `git.example.com/team`, **When** an image build clones any repository under that path (e.g. `git.example.com/team/service-c`), **Then** the build uses the project-scoped credential.
2. **Given** both a project-scoped credential for `git.example.com/team` and a repository-specific credential for `git.example.com/team/service-a` are configured, **When** an image build clones `git.example.com/team/service-a`, **Then** the build uses the repository-specific credential, not the project-scoped one.

---

### User Story 3 - Automatic selection of the most specific credential (Priority: P3)

As a platform operator, I don't want to manually order or prioritize my credential entries — when several configured credentials could apply to the same repository URL, the system should always pick the one that matches most precisely, on its own.

**Why this priority**: Removes a class of misconfiguration (wrong ordering, unpredictable precedence) once multiple credentials per domain are possible. Builds directly on User Stories 1 and 2 rather than introducing new configuration surface.

**Independent Test**: Configure a domain-wide credential for `git.example.com`, a project-scoped credential for `git.example.com/team`, and a repository-specific credential for `git.example.com/team/service-a`, all at once. Trigger builds against `git.example.com/other-team/x`, `git.example.com/team/service-b`, and `git.example.com/team/service-a`, and confirm each build uses the domain-wide, project-scoped, and repository-specific credential respectively.

**Acceptance Scenarios**:

1. **Given** three credentials of increasing specificity are configured for overlapping scopes on the same host, **When** builds run against repositories matching each scope, **Then** each build automatically uses the most specific credential that matches its repository URL, with no manual ordering required.

---

### Edge Cases

- What happens when two credential entries define the exact same scope (same host and same repository, or same host and same project path)? System MUST reject this configuration at startup with an error identifying the conflicting entries, rather than silently picking one.
- What happens when a repository's host has one or more credentials configured, but the repository's specific path doesn't match any repository- or project-scoped entry, and no domain-wide entry exists for that host? The repository is treated the same as a host with no configured credentials at all — the build clones it without authentication, matching today's behavior for untracked hosts.
- How is precedence resolved when project-scoped entries are nested (e.g. a credential for `git.example.com/group` and another for `git.example.com/group/subgroup`)? The entry whose path is the longest match against the repository's path wins.
- What happens when a project-scoped path is a string prefix of an unrelated repository's path but not a path-segment prefix (e.g. scope `team` vs. repository `team2/service-a`)? The scope MUST NOT match — only segment-aligned prefixes count.
- How is precedence resolved when a repository matches both an exact-host entry and a broader parent-domain entry with a more specific path (e.g. a project-scoped entry for the exact host `sub.example.com/team`, and a repository-specific entry for the parent domain `example.com/team/service-a`, both could apply to `sub.example.com/team/service-a`)? The exact-host match always wins over any parent-domain (subdomain-inherited) match, even though the parent-domain entry has the more specific path.
- What happens when an operator changes or removes a credential that a repository was previously relying on? The next build simply re-resolves credentials against the current configuration and falls back per the normal specificity rules (project-scoped, then domain-wide, then none).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an operator to configure more than one git credential entry for the same host/domain.
- **FR-002**: System MUST allow each credential entry to be scoped to exactly one of: a single specific repository (full repository path under a host), a project or group (a path prefix under a host covering multiple repositories), or the entire domain (no path — applies to any repository under the host not otherwise matched).
- **FR-002a**: System MUST match a project/group scope's path prefix only at path-segment boundaries: a scope of `team` MUST match repositories under `team/...` but MUST NOT match an unrelated repository whose path merely starts with the same characters (e.g. `team2/service-a`).
- **FR-003**: System MUST, when resolving credentials for a repository URL, select the configured credential whose scope most specifically matches that URL, ranked as follows (most specific first): (1) exact repository match on the URL's exact host, (2) project/group match on the exact host, (3) domain-wide match on the exact host, (4) exact repository match via a subdomain-inherited (parent-domain) host, (5) project/group match via a parent-domain host, (6) domain-wide match via a parent-domain host.
- **FR-003a**: System MUST match a credential entry against a repository URL whose host equals the entry's configured host, or whose host is a subdomain of the entry's configured host (e.g. an entry configured for `example.com` also matches `git.example.com`), consistent with today's single-credential-per-host matching.
- **FR-004**: System MUST support each credential entry independently specifying its own authentication material (username/password, token, or SSH key plus known-hosts), exactly as supported for a single credential per host today.
- **FR-005**: System MUST validate configuration at startup and reject it with an error identifying the conflicting entries when two or more credential entries define the identical scope for the same host.
- **FR-006**: System MUST continue to support a domain-wide-only credential entry (no repository or project path) so hosts that only need a single shared credential are unaffected by this change.
- **FR-007**: System MUST preserve current behavior for hosts with no matching credential entry at all: the repository clones without injected authentication.
- **FR-008**: System MUST continue to be configured entirely through the existing operator-managed static configuration mechanism (no new persisted entity, API, or UI is introduced by this feature).

### Key Entities

- **Git Credential Entry**: A named set of authentication material (username/password, token, or SSH key/known-hosts) for accessing git repositories over HTTPS or SSH, together with the scope it applies to.
- **Credential Scope**: The portion of a repository URL a Git Credential Entry applies to — a host plus, optionally, a path prefix under that host. No path means the entry is domain-wide; a partial path means project/group-scoped; a full repository path means repository-specific.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can add or change the credentials used for one specific repository without affecting authentication for any other repository on the same host.
- **SC-002**: An operator can grant one token access to every repository under a project/group with a single configuration entry, instead of one entry per repository.
- **SC-003**: When multiple configured credentials could apply to a repository, the correct (most specific) one is used automatically 100% of the time, with no manual ordering of entries required.
- **SC-004**: Configuration mistakes that would make credential selection ambiguous (duplicate identical scopes) are caught at startup, before any build attempts to use them.
- **SC-005**: Existing single-credential-per-domain configurations continue to work unchanged after upgrading to this feature.

## Assumptions

- Credential scope matching is based on the repository URL's host and path, consistent with how the existing single-credential-per-host feature already matches by host.
- "Project/group" scoping is expressed generically as a path prefix under a host, so it works uniformly across git hosting providers (GitHub organizations, GitLab groups/subgroups, Bitbucket projects, self-hosted servers) without provider-specific logic.
- This feature only changes how a credential is selected among several candidates; it does not add new authentication mechanisms beyond what a single credential entry already supports today.
- Configuration remains an operator/administrator concern set at deploy time, not an end-user- or API-exposed setting — consistent with how git and registry credentials are managed today.
