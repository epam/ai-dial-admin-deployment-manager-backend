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
The system SHALL allow more than one trusted private repo entry to share the same `host`, provided each is scoped differently — to a distinct repository path, a distinct project/group path, or (at most one) with no path at all (domain-wide). Two entries MAY additionally share an identical `(host, path)` scope when their authentication types differ (one SSH, one HTTPS/HTTP), since the URL's protocol selects between them.

Status: **Implemented**

#### Scenario: Repository-specific and domain-wide entries coexist
- **WHEN** `TRUSTED_PRIVATE_GIT_REPOS` configures a repository-specific entry for `git.example.com`/`team/service-a` and a domain-wide entry for `git.example.com`
- **THEN** both entries are accepted at startup

#### Scenario: Project-scoped entry covers multiple repositories
- **WHEN** a single entry is configured with `host: "git.example.com"`, `path: "team"`
- **THEN** it applies to every repository whose path starts with `team/` (e.g. `team/service-a`, `team/service-c`) that has no more specific entry of its own

#### Scenario: SSH and HTTPS entries coexist at the same scope
- **WHEN** one entry configures `sshKeyPath`/`sshKnownHostsPath` and another configures `user`/`token` for the same `host` and `path`
- **THEN** both entries are accepted at startup, an `ssh://`/`git@` clone URL resolves to the SSH entry, and an `https://` clone URL resolves to the `user`/`token` entry

### Requirement: Credential resolution picks the most specific match
When resolving credentials for a repository URL, the system SHALL first restrict candidates to entries whose authentication type matches the URL's protocol (SSH URLs → SSH-key entries; HTTPS/HTTP URLs → `user`/`token` entries), then select the single candidate whose scope most specifically matches it, ranked (most specific first):

1. Any match on the URL's exact host, ahead of any match inherited from a parent domain.
2. Among parent-domain (subdomain-inherited) matches, the closest — i.e. longest configured — host, ahead of any farther parent domain, **regardless of path specificity**.
3. Within the same configured host: exact-repository `path`, then project/group `path`, then domain-wide (no `path`); among nested project/group paths, the longest matching prefix.

Host comparison is case-insensitive; `path` comparison is case-sensitive, matching git's treatment of repository paths. No manual ordering of `TRUSTED_PRIVATE_GIT_REPOS` entries is required or honored.

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

#### Scenario: Closest parent domain beats a more specific path on a farther parent domain
- **WHEN** a repository on `a.b.example.com/team/service-a` matches both an entry for `b.example.com` with `path: "team"` and an entry for `example.com` with `path: "team/service-a"` (neither host matches exactly)
- **THEN** the `b.example.com` entry's credentials are used — the closer parent domain outranks the more specific path

#### Scenario: Path casing must match the repository URL
- **WHEN** an entry is configured with `path: "Team/Service-A"` and a build clones `https://git.example.com/team/service-a.git`
- **THEN** the entry does not match, and resolution falls back to any less specific matching entry (or to no credentials at all)

#### Scenario: Nested project scopes resolve to the longer prefix
- **WHEN** entries are configured for both `path: "group"` and `path: "group/subgroup"` on the same host, and a build clones a repository under `group/subgroup/`
- **THEN** the `group/subgroup` entry's credentials are used

#### Scenario: Project scope matches only at path-segment boundaries
- **WHEN** an entry is configured with `path: "team"`
- **THEN** it matches repositories under `team/…` but does not match an unrelated repository whose path merely starts with the same characters (e.g. `team2/service-a`)

### Requirement: Ambiguous (duplicate) scopes are rejected at startup
The system SHALL reject the configuration at application startup, before any build can run, when two or more entries resolve to the identical normalized `(host, path)` scope **for the same authentication type** (SSH, or HTTPS/HTTP). An entry that carries both an SSH key and `user`/`token` credentials occupies both authentication types at its scope.

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

#### Scenario: Same scope with different authentication types is not flagged
- **WHEN** two entries share the same `host` and `path` but one provides only `sshKeyPath`/`sshKnownHostsPath` and the other only `user`/`token`
- **THEN** both are accepted — the URL's protocol disambiguates them at resolution time

#### Scenario: Duplicate scope is reported as a configuration error
- **WHEN** the configuration JSON is syntactically valid but two entries conflict on scope and authentication type
- **THEN** startup fails with a message naming the conflicting host, path and authentication type — not with a JSON-format error

### Requirement: Path scoping selects a credential, it does not confine one
A `path` scope SHALL determine *which* configured credential is materialized for a build. It is not an access boundary: the resolved credential is written to the build's credential store for the whole host, so anything the build clones from that host (a submodule under a different path, for instance) may use it.

Status: **Implemented**

#### Scenario: Resolved credential is host-wide inside the build
- **WHEN** an entry scoped to `path: "team/service-a"` is resolved for a build
- **THEN** the generated `.git-credentials` entry covers the host (no path component, and `credential.useHttpPath` is not enabled), so a clone of another repository on that host from inside the same build can use it

### Requirement: Backward-compatible with single-credential-per-host configuration
Existing `TRUSTED_PRIVATE_GIT_REPOS` configurations that set no `path` on any entry SHALL continue to behave exactly as before this capability existed.

Status: **Implemented**

#### Scenario: Legacy single-entry-per-host configuration unaffected
- **WHEN** a configuration has at most one entry per host and none of them set `path`
- **THEN** credential resolution behaves identically to the original single-credential-per-host implementation

## Implementation Notes
- Configuration: `com.epam.aidial.deployment.manager.configuration.GitProperties` (processed model) / `GitPropertiesDto` (wire format), populated from the `TRUSTED_PRIVATE_GIT_REPOS` env var by `GitConfiguration.gitProperties()`
- Scope normalization (host lowercased with `Locale.ROOT`, so it never depends on the JVM default locale; path trimmed of leading/trailing `/` and a trailing `.git` suffix, casing preserved): `com.epam.aidial.deployment.manager.configuration.GitScopeUtils`
- Duplicate-scope validation: `GitConfiguration.validateNoDuplicateScopes()`, run once at startup after parsing, keyed on `(host, path, authentication type)`; it propagates as a configuration error rather than being wrapped as a JSON-format error
- Credential resolution (specificity-ranked matching): `com.epam.aidial.deployment.manager.service.pipeline.specification.GitService.findMatchingTrustedRepo()` / `matchRank()`
- Per-entry authentication-material validation (host required; user-or-sshKey required; password/token required with user; sshKey and sshKnownHosts required together) is unchanged from the original single-credential-per-host implementation
- Resolved credentials are materialized as a Kubernetes Secret (`.git-credentials`/`.gitconfig` for HTTPS, `id_rsa`/`known_hosts` for SSH) mounted into the BuildKit build pod — see `buildkit` spec
- Implemented via 027-multi-git-credentials
- Related specs: `image-builds`, `buildkit`, `container-registry` (the analogous, still single-credential-per-host, Docker registry configuration)
