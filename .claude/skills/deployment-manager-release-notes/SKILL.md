---
name: deployment-manager-release-notes
description: Use when the user asks to enhance, refine, polish, or "look at" the release notes for a tag — typically a fresh CI-generated pre-release (e.g. `0.16.0-rc.1`) or a stable cut. Reads the auto-generated notes off the GitHub release, classifies and rewrites each bullet in this project's editorial voice, adds the `Deployment Changes` pointer from `docs/INFRA-CHANGELOG.md` / `docs/upgrade-plans/` / `docs/configuration.md` / PR bodies, and saves a draft to `claude/release-notes/`. Never edits GitHub directly.
allowed-tools: Read Grep Glob LSP Bash(gh release view:*) Bash(gh release list:*) Bash(gh pr view:*) Bash(gh pr list:*) Bash(gh pr diff:*) Bash(git log:*) Bash(git show:*) Bash(git diff:*) Bash(git tag:*) Bash(git rev-parse:*) Bash(date:*) Write(claude/release-notes/*) Bash(mkdir -p claude/release-notes)
argument-hint: "[tag]"
arguments: tag
model: opus
effort: xhigh
context: fork
agent: general-purpose
---

# Deployment Manager release-notes enhancer

The CI publishes a release for every tag. The raw bullets come from `epam/ai-dial-ci`'s `generate_release_notes` action (wired through `.github/workflows/release.yml` → `java_release.yml`): it walks **commit subject lines** since the previous tag, routes them by conventional-commit *type* into `BREAKING CHANGES` / `Features` / `Fixes` / `Docs` / `Tests` / `CI` / `Other`, and sorts every section alphabetically. The router has a sharp edge: a plain `feat:` / `fix:` routes correctly and loses its prefix, but a **scoped** commit (`feat(scope):`, `fix(scope):`) slips past the router and lands in `Other` with the full prefix intact. So the raw bullets carry dirt — `feat(...)` / `fix(...)` items stranded in `Other`, lower-cased PR-title fragments (`always set progress-deadline …`), branch-style phrasing, and hotfix commits with no PR. The releases visible at `https://github.com/epam/ai-dial-admin-deployment-manager-backend/releases` from `0.13.0` through `0.16.0` show what those raw notes look like after a human editorial pass. This skill reproduces that pass.

You are running in a forked, isolated context. Read and research freely — only the final summary you return reaches the main conversation. All file writes happen in this fork; the draft lands at `claude/release-notes/<tag>-draft.md`.

## When to use

- "Enhance the release notes for `0.16.0-rc.1`"
- "Look at the latest pre-release notes and refine them"
- "Help me adjust release notes for the current rc"
- "The CI just published `<tag>`, make it readable"

Do **not** trigger on requests like "what changed in 0.15.0?" — that is a recall question, not a notes-editing task.

## Inputs

`tag` = `$tag` — the GitHub release tag to enhance (e.g. `0.16.0-rc.1`, `0.17.0`). If empty, pick the most recent tag from `gh release list --limit 5` and confirm with the user before editing.

## Workflow

### 1. Resolve target and reference styles

1. `gh release view <tag> --json body,name,tagName` — capture the raw CI notes.
2. `gh release list --limit 10` — locate the previous tag of the same kind (last stable for a stable release, the predecessor `rc` for a delta `rc.N+1`).
3. `gh release view <prev-stable-tag> --json body` and `gh release view <prev-rc-tag> --json body` (when relevant) — these are the style anchors. The established house style across `0.13.x`–`0.16.x` is **terse: one line per bullet**, issue ref first, no multi-paragraph descriptions. Match that terseness, not your own instincts.
4. `git tag --list | sort -V` + `git log <prev-tag>..<tag> --oneline` — full commit list for the range, so you can spot hotfix commits pushed directly (no PR) and confirm the alphabetical CI ordering against real chronology.

### 2. Pull source context for each bullet

For every bullet in the raw notes:

1. Parse out the trailing `(#<PR>)` (GitHub appends it to the squash-commit subject) and any leading `#<issue>` the author wrote in the PR title. DM's house format is `#<issue>` first, `(#<PR>)` last — keep that order.
2. `gh pr view <PR> --json title,body,labels` — read the PR body, not just the title. The body is where the *why* and the *what-it-replaces* live; the title is usually too compressed.
3. For bullets with no `(#<PR>)` (a commit pushed straight to `development`/`release-*` without a PR, e.g. `* fix tests`, `* correct ingress annotation default`), find the commit with `git log <prev-tag>..<tag> --oneline | grep -i <keywords>` and `git show <hash>` — these are usually hotfixes that should fold into a related entry, not stand alone.
4. If a PR body references a doc under `docs/` or a spec under `specs/`, skim it for the headline framing.

### 3. Cross-check `docs/configuration.md` / `docs/INFRA-CHANGELOG.md` / source for deployment changes

The `Deployment Changes` section is built from primary sources, not PR titles:

- `git diff <prev-tag>..<tag> -- docs/INFRA-CHANGELOG.md docs/configuration.md README.md` — env-var additions/removals/renames and the infra-changelog delta for this version.
- Every DM setting has **two names**: a Spring property path (e.g. `app.nim.deploy.url-schema`) and an environment variable (e.g. `K8S_NIM_DEPLOYMENT_URL_SCHEMA`). The operator-facing token is the **env var**. Verify it by reading `src/main/resources/application.yml` and the relevant `@ConfigurationProperties` class (via LSP `goToDefinition` on the field, or `Grep`), then confirm it against `docs/configuration.md`. PR bodies sometimes cite the Spring property path or a pre-rename name — the code and `application.yml` win. (0.16.0 caught `app.resources.*` → `app.validation.resources.*` this way: the property path moved but the env-var names did not.)
- Confirm defaults and bounds by reading the config class / validator, not the PR description.

### 4. Classify each bullet (rescue scoped items, keep the type sections, drop only noise)

The raw CI partition keys off the conventional-commit *type*. Plain `feat:` / `fix:` route correctly, but **scoped** commits (`feat(scope):`, `fix(scope):`) slip through to `Other` with their prefix — rescuing those is the main job here. **Keep the `BREAKING CHANGES`, `Docs`, `Tests`, and `CI` sections** the CI produces: a reader is better served by dedicated `Docs` / `Tests` / `CI` headings than by one overflowing `Other`. Reclassify by user impact:

| Where CI put it | Where it belongs | Rule |
|---|---|---|
| `Other` carrying a `feat(scope):` / `fix(scope):` prefix | `Features` / `Fixes` | The scope kept the router from placing it. Move it up and strip the prefix. |
| `Other` (a `chore:` that is actually a fix) | `Fixes` | e.g. a `chore:` security/CVE bump or a behavior correction. |
| `Features` / `Fixes` for a pre-release-only regression | `Fixes` with note "(affects pre-release users of <feature> only)" | Don't surface a transient bug as a feature. |
| A user-facing breaking change not flagged with `!` | also surface under `BREAKING CHANGES` | e.g. an env-var rename/removal operators must act on (see 0.15.0 `SECURITY_EMAIL_CLAIM` → `CLAIMS_EMAIL_KEY`). |
| Genuine docs / test / CI-workflow work | leave it in `Docs` / `Tests` / `CI` | Tidy the wording, but keep the section — don't dump it into `Other`. |
| Multiple PRs / hotfix commits on one change | one folded entry under the appropriate section | Cite all PR numbers `(#a, #b)` or commit hashes in parens. |

**Drop from the notes entirely** — only items with genuinely zero signal for any reader:

- `Merge branch …` / `Merge remote-tracking branch …` commits (the CI already strips `Merge branch`).
- `[skip ci] Update version` bumps (CI strips these too — flag any that slip through).
- Exact-duplicate bullets that have been folded into another entry.

**Keep — be inclusive, but file each item under its real section.** Surface internal and tooling work rather than dropping it: test work stays in `Tests`, documentation in `Docs`, CI-workflow changes in `CI`, and everything else that doesn't fit (spec-kit integration, Claude Code skills, dependency and GitHub-Actions bumps, internal renames visible in the API like `'type'` → `'$type'`, the infra-changelog creation itself) goes under `Other`. When unsure whether something is worth a line, keep it rather than dropping it. Surface borderline calls in the editorial-notes file (§8) instead of silently dropping.

### 5. Rewrite each kept bullet

The raw form is `* [#<issue>] <lower-cased PR-title fragment> (#<PR>)` for a routed item (prefix already stripped), or `* feat(scope): <fragment> (#<PR>)` for a scoped item stranded in `Other` (full prefix intact). Rewrite to:

```
* #<issue> <Active-voice description of what changed> (#<PR>)
```

Rules in order of importance:

1. **One line per bullet.** No multi-paragraph descriptions. If you need more detail, save it to the companion editorial-notes file (see §8), not the main draft.
2. **Drop the conventional prefix** left on a rescued scoped item (`feat(mcp):`, `fix(nim):`) and fold the scope into the prose instead.
3. **Lead with the issue ref, end with the PR ref:** `#<issue> … (#<PR>)`. Multiple PRs → `(#<a>, #<b>)`; multiple issues → `#<a>, #<b> …`; no issue → start the bullet with the description and keep `(#<PR>)` at the end. Don't strip these — the numbers auto-link on the release page.
4. **Keep it terse.** DM bullets are short statements of *what changed*, not *why*. Add a brief clarifying clause only when it materially helps a reader; do not force a "— why" clause (that is not DM's style). Use parentheticals (`(preview)`, `(regression fix)`) the way the published notes do.
5. **Quote code identifiers in single quotes** to match the established release style — env vars (`'K8S_NIM_INGRESS_CLASS_NAME'`), annotations (`'progress-deadline'`), config fields, API fields (`'$type'`), class names. (Backticks render as code and are fine in the upgrade-plan / infra-changelog docs, but the release body itself uses single quotes — match it.)
6. **Mark preview features** with an inline `(preview)` suffix, the way `Implement node pool selector (preview)` reads — not a `[Preview]` prefix.
7. **Flag regressions explicitly** with `(regression fix)` for items restoring previously-working behavior.
8. **Quote CVE IDs verbatim** for security upgrades, and keep the dependency/version detail (`Extend CVE-2025-59250 expiration date in .trivyignore, bump org.bouncycastle:bcprov-jdk18on from 1.79 to 1.84`).
9. **Re-order from the CI's alphabetical sort to a logical grouping.** Within each section, lead with the headline features, group related deployment/scaling/provider items, and trail dependency/tooling bumps — mirror how the published releases read, not the raw A–Z order.
10. **Capitalize the first word** of the description (the CI lower-cases PR-title fragments).

#### Example transformations

Each pair is `raw CI` → `enhanced`. These are reconstructed from real DM releases.

```
# Capitalize, add the (preview) marker:
- * #285 implement node pool selector (#295)
+ * #285 Implement node pool selector (preview) (#295)

# Scoped commit got stranded in Other (the scope kept it out of Features) — move it up, drop the prefix, expand:
- ## Other
- * feat(mcp): improve filtering for registry (#268)
+ ## Features
+ * Improve filtering for MCP registry (#268)

# Single-quote the identifier, fix casing:
- * #254 always set progress-deadline for nim and kserve model deployments (#261)
+ * #254 Always set 'progress-deadline' for NIM and KServe model deployments (#261)

# Promote an operator-facing env-var rename into BREAKING CHANGES, verify both names from application.yml:
- * migrate SECURITY_EMAIL_CLAIM env var to CLAIMS_EMAIL_KEY (#177)
+ ## BREAKING CHANGES
+ * Environment variable 'SECURITY_EMAIL_CLAIM' migrated to 'CLAIMS_EMAIL_KEY' (#177)

# Fold two PRs on one feature into a single entry:
- * #27 add export functionality (#153)
- * #27 add import functionality (#229)
+ * #27 Add export/import functionality (#153, #229)

# Keep test / docs / CI work in its own section — just tidy the wording, don't move it to Other:
- ## Tests
- * migrate all tests from JUnit Assertions to AssertJ (#274)
+ ## Tests
+ * Migrate all tests from JUnit Assertions to AssertJ (#274)

# Fold an orphan hotfix commit (pushed without a PR) into a related fix entry:
- * correct ingress annotation default     (orphan commit, no PR)
- * fix tests                              (orphan commit, no PR)
+ * Correct the default NIM ingress annotation so legacy external NIM URLs resolve (`a1b2c3d`, `e4f5g6h`)
```

### 6. Build the `Deployment Changes` section

In DM the `Deployment Changes` section is **a pointer, not a table.** It exists only when the range introduces env-var, behavioral, or schema/migration changes, and it links operators to the version's upgrade plan — which is itself generated from the infra changelog:

```markdown
## Deployment Changes

This release includes <high-priority | many critical and high-priority> changes. Please review the [full upgrade guide](https://github.com/epam/ai-dial-admin-deployment-manager-backend/blob/<tag>/docs/upgrade-plans/<version>.md) before proceeding.
```

To produce it:

1. Check whether `docs/upgrade-plans/<version>.md` exists for this release and whether `docs/INFRA-CHANGELOG.md` has a `## <version>` block. The infra changelog (Added / Changed / Removed × subcategory, each row carrying the Spring property path **and** env var **and** default) is the source of truth; the upgrade plan (priority-tiered Critical / High / Medium / Low + Helm `values.yaml` snippets + pre-flight checklist) is auto-generated from it.
2. Pick the severity wording from the upgrade plan's populated tiers — `many critical and high-priority changes` when the Critical/High sections have entries, `high-priority changes` for a lighter release. Match the phrasing the previous stable release used.
3. **Pin the link to a tag that actually has the file.** Stable releases point at the stable tag (`blob/0.15.0/…`); a release cut before its own upgrade plan landed may need to point at the rc tag that carried it (0.16.0 points at `blob/0.16.0-rc.0/…`). Verify the path resolves.
4. **Do not inline env-var tables, Helm snippets, or migration steps in the release body.** That detail lives in the upgrade plan and infra changelog. If the upgrade plan or the infra-changelog block is **missing** for a version that clearly has infra changes, do not invent one — flag it in the editorial-notes file as an open item for the maintainer.

Omit the whole section for a release with no env-var / behavioral / schema change (most delta `rc`s).

### 7. Pre-release / delta handling

If the target is `<X.Y.Z>-rc.N` with `N ≥ 1`:

- The release covers only what changed since the previous rc — do **not** consolidate or rewrite the predecessor's notes. Each pre-release tag has its own GitHub release page; the consolidation happens at the stable cut.
- Drop sections that have no entries in the delta (a small rc is often just `## Other`, as `0.16.0-rc.1` was — and carries no `Deployment Changes` pointer when no env vars or schema changes landed).
- Do **not** prepend a "Delta since <prev-rc>" pointer at the top. The CI doesn't emit one, the shipped rc notes don't carry one, and the `-rc.N` suffix already signals what the release is. Adding a header just creates editorial noise the user has to clean up.

### 8. Save the draft (and optional editorial companion)

Create `claude/release-notes/` if missing, then write:

- **`claude/release-notes/<tag>-draft.md`** — the final notes, ready to paste into the GitHub release body. No preamble, no commentary — just the headings and bullets.
- **`claude/release-notes/<tag>-editorial-notes.md`** *(optional)* — only when there are non-obvious calls worth surfacing to the user:
  - Rename mapping (raw bullet → enhanced bullet) for items where the rewrite is non-trivial.
  - List of items dropped or folded, with one-line reason per item.
  - Open questions for the user (e.g. "Keep the `Bump the github-actions group` chore under Other?").
  - Any place the source-of-truth diverged from the PR body (e.g. canonical env-var name, a missing `docs/upgrade-plans/<version>.md`).

### 9. Verify nothing was pushed to GitHub

This skill **never** runs `gh release edit`, `gh release create`, or any write operation against the repo. Everything is drafted in local files under `claude/release-notes/`. If the user later asks you to apply, that is a separate, explicit request.

## Output format

The file saved to `claude/release-notes/<tag>-draft.md` follows this shape:

```markdown
## BREAKING CHANGES

* <only when the range has a breaking / operator-action item>

## Features

* #<issue> <one bullet per change> (#<PR>)

## Fixes

* #<issue> <one bullet per change> (#<PR>)

## Docs

* <documentation changes>

## Tests

* <test additions / migrations>

## CI

* <CI / workflow changes>

## Other

* <chore-type items and anything that doesn't fit the sections above — spec-kit, Claude Code skills, dependency / GitHub-Actions bumps, API-visible renames>

## Deployment Changes

This release includes <severity> changes. Please review the [full upgrade guide](https://github.com/epam/ai-dial-admin-deployment-manager-backend/blob/<tag>/docs/upgrade-plans/<version>.md) before proceeding.
```

Section order follows the CI: `BREAKING CHANGES` → `Features` → `Fixes` → `Docs` → `Tests` → `CI` → `Other` → `Deployment Changes`. Emit only the sections that have entries.

A delta `rc` release uses the same shape with only the populated sections — no preamble paragraph, no header pointing at the previous rc.

## Return to the main conversation

Return a short summary — five lines or fewer. Include:

- The draft path (`claude/release-notes/<tag>-draft.md`).
- Counts of bullets per section after enhancement.
- Reclassifications that happened (e.g. "rescued 2 scoped `feat(...)` bullets from Other → Features, promoted 1 to BREAKING CHANGES").
- Items dropped or folded (count, with one example).
- Whether a `Deployment Changes` pointer was added and which upgrade-plan tag it links to.
- Any open questions for the user (env-var-name disagreement between PR body and source, a missing upgrade plan, ambiguous categorization).

Example:

> Drafted `claude/release-notes/0.17.0-rc.1-draft.md`. 5 Features, 3 Fixes, 2 Docs, 1 Tests, 1 CI, 3 Other. Rescued 2 scoped `feat(...)` bullets from Other → Features and promoted 1 env-var rename to BREAKING CHANGES. Folded 2 orphan hotfix commits into a related fix. Added the Deployment Changes pointer to `blob/0.17.0-rc.1/docs/upgrade-plans/0.17.0.md`. One open: PR body says `app.resources.max-storage-size` but `application.yml` calls it `app.validation.resources.max-storage-size` — used the code name; flagged in editorial notes.

## Safety rails

- **Never edit GitHub.** No `gh release edit`, no `gh release create`. Drafts only.
- **Never invent items.** Every kept bullet maps to a PR or a commit hash in the range.
- **Never silently drop or rename a PR reference.** The bullet keeps its canonical `#<issue> … (#<PR>)` so links resolve on the release page.
- **Verify canonical env-var names from `application.yml` / the config class**, not from PR bodies. PR descriptions have used Spring property paths or pre-rename names; the code is the source of truth.
- **Don't inline the upgrade-plan detail** into the release body — `Deployment Changes` is a pointer; the tables live in `docs/upgrade-plans/<version>.md` and `docs/INFRA-CHANGELOG.md`.
- **Don't consolidate pre-release notes** into the stable's notes unless the user explicitly asks — each rc tag has its own page.
- **Match the terseness and issue-first format of the published releases.** One line per bullet, `#<issue>` first, `(#<PR>)` last, identifiers in single quotes.

## Maintenance

Conventions drift as the project grows. If you notice a pattern the raw CI notes produce that this skill doesn't handle (a new conventional-commit scope, a new machine section from `epam/ai-dial-ci`, a recurring rewrite the user keeps asking for), or a change in the infra-changelog → upgrade-plan pipeline, surface it in your return summary and offer to update this `SKILL.md`. The user can confirm before any edit lands.
