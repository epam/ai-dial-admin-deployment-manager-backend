# Phase 1 Data Model: Multiple Git Credentials per Domain

This feature has no database entity — configuration lives entirely in memory, parsed at startup from the `TRUSTED_PRIVATE_GIT_REPOS` env var (spec FR-008). "Data model" here describes the in-memory shapes and the resolution logic that operates on them.

## Entity: Trusted Private Git Repo entry (Git Credential Entry)

Represented at two layers, as today: a wire-format DTO parsed from JSON, and a processed model with file-path fields resolved to file contents.

### `GitPropertiesDto.TrustedPrivateGitRepoDto` (wire format, JSON array element of `TRUSTED_PRIVATE_GIT_REPOS`)

| Field | Type | Required | Notes |
|---|---|---|---|
| `host` | String | Yes | Unchanged. Hostname the entry applies to (matched exactly or via subdomain — FR-003a). |
| `path` | String | No (**new**) | Optional repository/project path under `host`. Absent = domain-wide entry (FR-002, FR-006). Normalized per research D2 before use. |
| `protocol` | String | No (default `https`) | Unchanged. |
| `user` | String | No | Unchanged. |
| `password` | String | No | Unchanged. |
| `token` | String | No | Unchanged. |
| `sshKeyPath` | String | No | Unchanged — file path read at startup into `sshKey` content. |
| `sshKnownHostsPath` | String | No | Unchanged — file path read at startup into `sshKnownHosts` content. |

### `GitProperties.TrustedPrivateGitRepo` (processed model, held on the `GitProperties` bean)

Same fields as the DTO, minus the two `*Path` fields, plus their resolved content (`sshKey`, `sshKnownHosts`) — unchanged from today except for the added `path` field:

| Field | Type | Notes |
|---|---|---|
| `host` | String | |
| `path` | String (nullable) | **new** — carried through from the DTO unchanged (already normalized at parse time, see below). |
| `protocol` | String | default `https` |
| `user` | String | |
| `password` | String | |
| `token` | String | |
| `sshKey` | String | file content, not path |
| `sshKnownHosts` | String | file content, not path |

## Validation rules

Existing per-entry rules (`GitConfiguration.validateRepoConfiguration`) are unchanged:

1. `host` MUST be set.
2. Either `user` or `sshKeyPath` MUST be set.
3. `sshKeyPath` and `sshKnownHostsPath` MUST be set together (both or neither).
4. If `user` is set, `password` or `token` MUST also be set.
5. If `password` is set, `user` MUST also be set.

New cross-entry rule (FR-005, research D4):

6. After normalizing `host` (case-insensitive) and `path` (trimmed, `.git`-suffix stripped, per research D2) across **all** entries, no two entries may share the same `(host, path)` pair — including two entries that both omit `path` for the same `host`. Violation MUST fail startup with an error identifying the conflicting entries (e.g. their position/index in the source list, and the shared `host`/`path`).

Rule 6 deliberately does **not** flag entries whose scopes merely overlap without being identical (e.g. `team` and `team/service-a` on the same host) — that is the expected nested-specificity case resolved by the algorithm below, not a configuration error (see spec Edge Cases).

## Resolution logic: selecting a credential for a repository URL

Not a stored entity, but the core new behavior (spec FR-003), implemented in `GitService`:

**Input**: a repository URL (HTTPS or SSH form).

**Steps**:
1. Extract `(host, path)` from the URL (research D5); normalize `path` the same way as configured entries (research D2).
2. For each configured `TrustedPrivateGitRepo`, determine if it matches:
   - Host match: exact equality, or the URL's host is a subdomain of the entry's host (`host.endsWith("." + entry.host)`).
   - Path match: entry has no `path` (always matches, domain-wide); or entry's `path` equals the URL's `path` exactly (repository-specific); or entry's `path` is a segment-prefix of the URL's `path` — i.e. `path.equals(entry.path)` or `path.startsWith(entry.path + "/")` (project/group scope, FR-002a).
   - Auth-type match: unchanged from today — SSH URLs only match entries with `sshKey` set; HTTPS URLs only match entries with `user` or `token` set.
3. Among all matching entries, select the one with the best specificity tuple `(hostRank, pathRank, pathLength)` as defined in research D3.
4. If no entry matches, behave as today for untracked hosts: no credentials are injected, and the repository clones without authentication (FR-007).

**Relationships**: One repository URL resolves to at most one `TrustedPrivateGitRepo` at a time (never more — the tuple ordering always yields a single best match, ties being structurally impossible once rule 6 above holds, since any two entries at the same `hostRank`/`pathRank`/`pathLength` implies identical `(host, path)`, which validation already rejects).

## State / lifecycle

None — configuration is immutable for the lifetime of the process (loaded once at startup by `GitConfiguration.gitProperties()`); there is no update/delete flow (consistent with FR-008: not exposed via any API).
