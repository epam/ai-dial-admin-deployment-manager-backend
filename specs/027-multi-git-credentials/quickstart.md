# Quickstart: Multiple Git Credentials per Domain

No UI or API is involved (spec FR-008) — this is verified entirely through configuration and image-build behavior.

## 1. Configure three overlapping scopes on one host

Set `TRUSTED_PRIVATE_GIT_REPOS`:

```json
[
  { "host": "git.example.com", "path": "team/service-a", "user": "svc", "token": "repo-specific-token" },
  { "host": "git.example.com", "path": "team", "user": "svc", "token": "project-wide-token" },
  { "host": "git.example.com", "user": "svc", "password": "fallback-password" }
]
```

Start the application. Startup MUST succeed (these three scopes are distinct: an exact repo path, a project prefix, and domain-wide — not a conflict).

## 2. Verify repository-specific credential wins (User Story 1)

Trigger an image build from a `GitDockerfileImageSource` pointing at `https://git.example.com/team/service-a.git`.

**Expected**: the build's git secret uses `repo-specific-token` (verify via the generated `.git-credentials` content, or by confirming the build can clone a repo only that token has access to).

## 3. Verify project-wide credential covers sibling repos (User Story 2)

Trigger a build from `https://git.example.com/team/service-c.git` (no repository-specific entry exists for this one).

**Expected**: the build uses `project-wide-token` — the entry scoped to `team`, not the `team/service-a`-specific entry and not the domain-wide fallback.

## 4. Verify domain-wide fallback still applies outside the project (User Story 3)

Trigger a build from `https://git.example.com/other-team/x.git`.

**Expected**: the build uses the domain-wide `fallback-password` entry — no `path`-scoped entry matches this repository.

## 5. Verify duplicate-scope configuration is rejected at startup (FR-005)

Change the config to include two entries with the identical scope, e.g.:

```json
[
  { "host": "git.example.com", "path": "team", "user": "a", "token": "x" },
  { "host": "git.example.com", "path": "team", "user": "b", "token": "y" }
]
```

**Expected**: the application fails to start with an `IllegalArgumentException` naming both conflicting entries (host `git.example.com`, path `team`, `HTTPS/HTTP` authentication), reported as a configuration error rather than a JSON-format error.

Now change the second entry to use SSH instead:

```json
[
  { "host": "git.example.com", "path": "team", "user": "a", "token": "x" },
  { "host": "git.example.com", "path": "team", "sshKeyPath": "/etc/git/id_rsa", "sshKnownHostsPath": "/etc/git/known_hosts" }
]
```

**Expected**: startup succeeds — the two entries serve different authentication types, so an `https://` clone resolves to the first and a `git@`/`ssh://` clone to the second.

## 6. Verify existing single-entry-per-host configs are unaffected (SC-005)

Revert to a config with exactly one entry per host and no `path` field (today's format). Trigger a build against that host.

**Expected**: identical behavior to before this feature — the single entry is used, same as today.
