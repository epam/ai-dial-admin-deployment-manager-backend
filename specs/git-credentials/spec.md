# Git Credentials

## Purpose
This spec describes how the service authenticates when cloning private Git repositories during image builds — the trusted-repository credential configuration and how a matching credential is selected for a given repository URL.

Status: **Implemented**

## Key Terms
- **Trusted private repo entry**: One configured credential in `TRUSTED_PRIVATE_GIT_REPOS`, scoped to a host and, optionally, a repository or project/group path under that host.
- **Domain-wide entry**: A trusted private repo entry with no `path` — applies to any repository under its host not covered by a more specific entry.
- **Project/group-scoped entry**: An entry whose `path` is a segment-prefix of the target repository's path (e.g. `path: "team"` covers `team/service-a`, `team/service-b`, …).
- **Repository-specific entry**: An entry whose `path` equals the target repository's exact path.
- **Subdomain-inherited match**: A configured `host` matching a repository URL whose host is a subdomain of it (e.g. `host: "example.com"` matching `git.example.com`).

## Requirements

### Requirement: Multiple credential entries per host, each independently scoped
The system SHALL allow more than one trusted private repo entry to share the same `host`, provided each is scoped differently — to a distinct repository path, a distinct project/group path, or (at most one) with no path at all (domain-wide).

Status: **Implemented**

#### Scenario: Repository-specific and domain-wide entries coexist
- **WHEN** `TRUSTED_PRIVATE_GIT_REPOS` configures a repository-specific entry for `git.example.com`/`team/service-a` and a domain-wide entry for `git.example.com`
- **THEN** both entries are accepted at startup

#### Scenario: Project-scoped entry covers multiple repositories
- **WHEN** a single entry is configured with `host: "git.example.com"`, `path: "team"`
- **THEN** it applies to every repository whose path starts with `team/` (e.g. `team/service-a`, `team/service-c`) that has no more specific entry of its own

### Requirement: Credential resolution picks the most specific match
When resolving credentials for a repository URL, the system SHALL select the single configured entry whose scope most specifically matches it, ranked (most specific first): (1) exact-repository match on the exact host, (2) project/group match on the exact host, (3) domain-wide match on the exact host, (4) exact-repository match via a subdomain-inherited host, (5) project/group match via a subdomain-inherited host, (6) domain-wide match via a subdomain-inherited host. No manual ordering of `TRUSTED_PRIVATE_GIT_REPOS` entries is required or honored.

Status: **Implemented**

#### Scenario: Repository-specific entry wins over domain-wide
- **WHEN** both a repository-specific entry for `team/service-a` and a domain-wide entry exist on the same host, and a build clones `team/service-a`
- **THEN** the repository-specific entry's credentials are used

#### Scenario: Domain-wide entry used outside any scoped path
- **WHEN** a build clones a repository on a host that has project- or repository-scoped entries, but the repository's path matches none of them
- **THEN** the host's domain-wide entry (if any) is used

#### Scenario: Exact host beats subdomain-inherited host regardless of path specificity
- **WHEN** an exact-host entry (e.g. `host: "sub.example.com"`) and a subdomain-inherited entry from a parent domain (e.g. `host: "example.com"`) could both match a repository on `sub.example.com`, even if the parent-domain entry has a more specific path
- **THEN** the exact-host entry's credentials are used

#### Scenario: Nested project scopes resolve to the longer prefix
- **WHEN** entries are configured for both `path: "group"` and `path: "group/subgroup"` on the same host, and a build clones a repository under `group/subgroup/`
- **THEN** the `group/subgroup` entry's credentials are used

#### Scenario: Project scope matches only at path-segment boundaries
- **WHEN** an entry is configured with `path: "team"`
- **THEN** it matches repositories under `team/…` but does not match an unrelated repository whose path merely starts with the same characters (e.g. `team2/service-a`)

### Requirement: Ambiguous (duplicate) scopes are rejected at startup
The system SHALL reject the configuration at application startup, before any build can run, when two or more entries resolve to the identical normalized `(host, path)` scope.

Status: **Implemented**

#### Scenario: Duplicate domain-wide entries
- **WHEN** two entries configure the same `host` and neither has a `path`
- **THEN** the application fails to start with an error naming the conflicting entries

#### Scenario: Duplicate scoped entries
- **WHEN** two entries configure the same `host` and the same `path` (after normalizing leading/trailing slashes and a trailing `.git` suffix)
- **THEN** the application fails to start with an error naming the conflicting entries

#### Scenario: Overlapping-but-distinct scopes are not flagged
- **WHEN** one entry has `path: "team"` and another has `path: "team/service-a"` on the same host
- **THEN** both are accepted — this is the expected nested-specificity case, not a conflict

### Requirement: Backward-compatible with single-credential-per-host configuration
Existing `TRUSTED_PRIVATE_GIT_REPOS` configurations that set no `path` on any entry SHALL continue to behave exactly as before this capability existed.

Status: **Implemented**

#### Scenario: Legacy single-entry-per-host configuration unaffected
- **WHEN** a configuration has at most one entry per host and none of them set `path`
- **THEN** credential resolution behaves identically to the original single-credential-per-host implementation

## Implementation Notes
- Configuration: `com.epam.aidial.deployment.manager.configuration.GitProperties` (processed model) / `GitPropertiesDto` (wire format), populated from the `TRUSTED_PRIVATE_GIT_REPOS` env var by `GitConfiguration.gitProperties()`
- Scope normalization (host lowercased; path trimmed of leading/trailing `/` and a trailing `.git` suffix): `com.epam.aidial.deployment.manager.configuration.GitScopeUtils`
- Duplicate-scope validation: `GitConfiguration.validateNoDuplicateScopes()`, run once at startup after parsing
- Credential resolution (specificity-ranked matching): `com.epam.aidial.deployment.manager.service.pipeline.specification.GitService.findMatchingTrustedRepo()` / `matchRank()`
- Per-entry authentication-material validation (host required; user-or-sshKey required; password/token required with user; sshKey and sshKnownHosts required together) is unchanged from the original single-credential-per-host implementation
- Resolved credentials are materialized as a Kubernetes Secret (`.git-credentials`/`.gitconfig` for HTTPS, `id_rsa`/`known_hosts` for SSH) mounted into the BuildKit build pod — see `buildkit` spec
- Implemented via 027-multi-git-credentials
- Related specs: `image-builds`, `buildkit`, `container-registry` (the analogous, still single-credential-per-host, Docker registry configuration)
