# Contract: `TRUSTED_PRIVATE_GIT_REPOS` configuration schema

This is not a REST/API contract — this feature adds no endpoint (spec FR-008). It is the operator-facing config contract: the shape of the JSON payload the `TRUSTED_PRIVATE_GIT_REPOS` env var accepts, since that payload is the only interface this feature exposes.

## Shape

A JSON array of entries. Each entry:

```json
{
  "host": "string, required",
  "path": "string, optional — omit for a domain-wide entry",
  "protocol": "string, optional, default \"https\"",
  "user": "string, optional",
  "password": "string, optional",
  "token": "string, optional",
  "sshKeyPath": "string, optional — absolute path to an SSH private key file",
  "sshKnownHostsPath": "string, optional — absolute path to a known_hosts file"
}
```

`path` is the only new field versus today's schema. Everything else is byte-for-byte unchanged.

## Per-entry validation (unchanged)

- `host` MUST be present.
- Either `user` or `sshKeyPath` MUST be present.
- `sshKeyPath` and `sshKnownHostsPath` MUST be present together, or neither.
- If `user` is present, `password` or `token` MUST also be present.
- If `password` is present, `user` MUST also be present.

Violating any of these fails application startup with `IllegalArgumentException` (unchanged behavior).

## Cross-entry validation (new)

- No two entries may resolve to the same normalized `(host, path)` pair **for the same authentication type** — SSH (`sshKeyPath`) or HTTPS/HTTP (`user`/`token`); an entry carrying both occupies both (see `data-model.md` for normalization rules). Violating this fails application startup with `IllegalArgumentException` naming the conflicting entries, their shared host/path and the authentication type.
- Two entries sharing a `(host, path)` scope with *different* authentication types are valid: an SSH clone URL resolves to the SSH entry and an HTTPS clone URL to the `user`/`token` entry.

## Examples

**Repository-specific credential** (User Story 1):

```json
{ "host": "git.example.com", "path": "team/service-a", "user": "svc", "token": "repo-a-token" }
```

**Project-wide credential covering every repo under a group** (User Story 2):

```json
{ "host": "git.example.com", "path": "team", "user": "svc", "token": "project-wide-token" }
```

**SSH and HTTPS credentials for the same scope** (valid — the clone URL's protocol selects between them):

```json
[
  { "host": "git.example.com", "path": "team/service-a", "sshKeyPath": "/etc/git/id_rsa", "sshKnownHostsPath": "/etc/git/known_hosts" },
  { "host": "git.example.com", "path": "team/service-a", "user": "svc", "token": "repo-a-token" }
]
```

**Domain-wide fallback** (today's existing shape, still valid — `path` simply omitted):

```json
{ "host": "git.example.com", "user": "svc", "password": "fallback-password" }
```

**All three combined on one host** (User Story 3 — most specific wins automatically):

```json
[
  { "host": "git.example.com", "path": "team/service-a", "token": "repo-specific-token" },
  { "host": "git.example.com", "path": "team", "token": "project-wide-token" },
  { "host": "git.example.com", "user": "svc", "password": "fallback-password" }
]
```

A build cloning `git.example.com/team/service-a` uses the first entry; `git.example.com/team/service-b` uses the second; `git.example.com/other-team/x` uses the third.

## Backward compatibility

Any payload valid under today's schema (no entry has `path`) remains valid and behaves identically — every entry is treated as domain-wide, and with at most one entry per host (today's only supported configuration), resolution is unchanged (spec SC-005).
